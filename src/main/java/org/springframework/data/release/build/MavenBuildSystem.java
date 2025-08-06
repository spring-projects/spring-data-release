/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.release.build;

import static org.springframework.data.release.build.CommandLine.Argument.*;
import static org.springframework.data.release.build.CommandLine.Goal.*;
import static org.springframework.data.release.model.Projects.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.release.build.CommandLine.Argument;
import org.springframework.data.release.build.CommandLine.Goal;
import org.springframework.data.release.build.Pom.Artifact;
import org.springframework.data.release.deployment.DefaultDeploymentInformation;
import org.springframework.data.release.deployment.DeploymentInformation;
import org.springframework.data.release.deployment.DeploymentProperties;
import org.springframework.data.release.deployment.DeploymentProperties.Authentication;
import org.springframework.data.release.deployment.DeploymentProperties.MavenCentral;
import org.springframework.data.release.deployment.StagingRepository;
import org.springframework.data.release.git.Branch;
import org.springframework.data.release.git.BranchMapping;
import org.springframework.data.release.git.GitProject;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.*;
import org.springframework.data.release.utils.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.xmlbeam.ProjectionFactory;
import org.xmlbeam.XBProjector;
import org.xmlbeam.dom.DOMAccess;
import org.xmlbeam.io.StreamInput;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 * @author Greg Turnquist
 */
@Component
@Order(100)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MavenBuildSystem implements BuildSystem {

	static String POM_XML = "pom.xml";

	Workspace workspace;
	ProjectionFactory projectionFactory;
	Logger logger;
	MavenRuntime mvn;
	DeploymentProperties properties;
	Gpg gpg;

	Environment env;

	static final String REPO_OPENING_TAG = "<repository>";
	static final String REPO_CLOSING_TAG = "</repository>";

	@Override
	public BuildSystem withJavaVersion(JavaVersion javaVersion) {
		return new MavenBuildSystem(workspace, projectionFactory, logger, mvn.withJavaVersion(javaVersion), properties, gpg,
				env);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.build.BuildSystem#updateProjectDescriptors(org.springframework.data.release.model.ModuleIteration, org.springframework.data.release.model.TrainIteration, org.springframework.data.release.model.Phase)
	 */
	@Override
	public <M extends ProjectAware> M updateProjectDescriptors(M module, UpdateInformation information) {

		PomUpdater updater = new PomUpdater(logger, information, module.getSupportedProject());
		TrainIteration train = information.getTrain();

		if (updater.isBuildProject()) {

			if (information.isBomInBuildProject()) {
				updateBom(updater, information, "bom/pom.xml", train.getSupportedProject(BUILD));
			}

			updateParentPom(updater, information);
		} else if (updater.isBomProject()) {
			updateBom(updater, information, "bom/pom.xml", train.getSupportedProject(BOM));
		} else {

			doWithProjection(workspace.getFile(POM_XML, updater.getProject()), pom -> {

				updater.updateDependencyProperties(pom);
				updater.updateParentVersion(pom);
				updater.updateRepository(pom);
			});
		}

		return module;
	}

	@Override
	@SneakyThrows
	public ModuleIteration updateBuildConfig(ModuleIteration module, BranchMapping branches) {

		File jenkinsfile = workspace.getFile("Jenkinsfile", module.getSupportedProject());
		if (!jenkinsfile.exists()) {
			logger.warn(module, "No Jenkinsfile found, skipping Jenkinsfile update.");
		}

		byte[] bytes = Files.readAllBytes(jenkinsfile.toPath());
		String content = new String(bytes, StandardCharsets.UTF_8);

		for (ModuleIteration participatingModule : module.getTrainIteration()) {

			Branch source = branches.getSourceBranch(participatingModule.getProject());
			Branch target = branches.getTargetBranch(participatingModule.getProject());

			if (source == null || target == null) {
				continue;
			}

			GitProject project = GitProject.of(participatingModule.getSupportedProject());

			String dependency = "%s/%s".formatted(project.getRepositoryName(), source);
			String replacement = "%s/%s".formatted(project.getRepositoryName(), target);

			content = content.replace(dependency, replacement);
		}

		Files.write(jenkinsfile.toPath(), content.getBytes(StandardCharsets.UTF_8));

		logger.warn(module, "Jenkinsfile updated.");

		return module;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.build.BuildSystem#prepareVersion(org.springframework.data.release.model.ModuleIteration, org.springframework.data.release.model.Phase)
	 */
	@Override
	public ModuleIteration prepareVersion(ModuleIteration module, Phase phase) {

		SupportedProject project = module.getSupportedProject();
		UpdateInformation information = UpdateInformation.of(module.getTrainIteration(), phase);

		CommandLine goals = CommandLine.of(goal("versions:set"), goal("versions:commit"));

		if (BOM.equals(module.getProject())) {

			mvn.execute(project, goals.and(arg("newVersion").withValue(information.getReleaseTrainVersion())) //
					.and(arg("generateBackupPoms").withValue("false")) //
					.andIf(module.isCommercial(), profile("spring-enterprise")));

			mvn.execute(project, goals.and(arg("newVersion").withValue(information.getReleaseTrainVersion())) //
					.and(arg("generateBackupPoms").withValue("false")) //
					.and(arg("processAllModules").withValue("true")) //
					.and(Argument.of("-pl").withValue("bom")) //
					.andIf(module.isCommercial(), profile("spring-enterprise")));

		} else {
			mvn.execute(project,
					goals.and(arg("newVersion").withValue(information.getProjectVersionToSet(project.getProject())))
							.and(arg("generateBackupPoms").withValue("false")) //
							.andIf(module.isCommercial(), profile("spring-enterprise")));
		}

		if (BUILD.equals(module.getProject())) {

			if (!module.getTrain().usesCalver()) {
				mvn.execute(project, goals.and(arg("newVersion").withValue(information.getReleaseTrainVersion())) //
						.and(arg("generateBackupPoms").withValue("false")) //
						.and(arg("groupId").withValue("org.springframework.data")) //
						.and(arg("artifactId").withValue("spring-data-releasetrain")) //
						.andIf(module.isCommercial(), profile("spring-enterprise")));
			}

			mvn.execute(project, CommandLine.of(Goal.INSTALL).andIf(module.isCommercial(), profile("spring-enterprise")));
		}

		return module;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.build.BuildSystem#triggerPreReleaseCheck(org.springframework.data.release.model.ModuleIteration)
	 */
	public <M extends ProjectAware> M triggerPreReleaseCheck(M module) {

		mvn.execute(module.getSupportedProject(), CommandLine.of(Goal.CLEAN, Goal.VALIDATE, profile("pre-release")));

		return module;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.build.BuildSystem#triggerBuild(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public <M extends ProjectAware> M triggerBuild(M module) {

		Argument profile = module.isCommercial() ? profile("ci,release,spring-enterprise") : profile("ci,release");
		CommandLine arguments = CommandLine.of(Goal.CLEAN, Goal.INSTALL, //
				profile, //
				arg("gpg.executable").withValue(gpg.getExecutable()), //
				arg("gpg.keyname").withValue(gpg.getKeyname()), //
				arg("gpg.passphrase").withValue(gpg.getPassphrase()))//
				.andIf(module.getSupportedProject().getProject().skipTests(), SKIP_TESTS)
				.andIf(!ObjectUtils.isEmpty(properties.getSettingsXml()), settingsXml(properties.getSettingsXml()));

		mvn.execute(module.getSupportedProject(), arguments);

		return module;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.build.BuildSystem#deploy(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public DeploymentInformation deploy(ModuleIteration module) {

		Assert.notNull(module, "Module must not be null!");

		DeploymentInformation information = new DefaultDeploymentInformation(module, properties);

		deploy(module, information);

		return information;
	}

	@Override
	public DeploymentInformation deploy(ModuleIteration module, StagingRepository stagingRepository) {

		Assert.notNull(module, "Module must not be null!");
		Assert.notNull(stagingRepository, "StagingRepository must not be null!");

		DeploymentInformation information = new DefaultDeploymentInformation(module, properties, stagingRepository);

		deploy(module, information);

		return information;
	}

	private void deploy(ModuleIteration module, DeploymentInformation information) {

		Assert.notNull(module, "Module must not be null!");
		Assert.notNull(information, "DeploymentInformation must not be null!");

		deployToArtifactory(module, information);

		doDeploy(module, information);
	}

	/**
	 * Triggers Maven commands to deploy module artifacts to Spring Artifactory.
	 *
	 * @param module must not be {@literal null}.
	 * @param information must not be {@literal null}.
	 */
	private void deployToArtifactory(ModuleIteration module, DeploymentInformation information) {

		Assert.notNull(module, "Module iteration must not be null!");
		Assert.notNull(information, "Deployment information must not be null!");

		if (!module.isCommercial()) {
			logger.log(module, "Not a commercial release. Skipping Artifactory deployment.");
			return;
		}

		logger.log(module, "Deploying artifacts to Spring Commercial Artifactory…");

		Authentication authentication = properties.getAuthentication(module);

		Gpg gpg = getGpg();

		CommandLine arguments = CommandLine.of(Goal.CLEAN, Goal.DEPLOY, //
				profile("ci,release,artifactory"), //
				SKIP_TESTS, //
				arg("artifactory.server").withValue(authentication.getServer().getUri()),
				arg("artifactory.staging-repository").withValue(authentication.getStagingRepository()),
				arg("artifactory.username").withValue(authentication.getUsername()),
				arg("artifactory.password").withValue(authentication.getApiKey()),
				arg("artifactory.build-name").withQuotedValue(information.getBuildName()),
				arg("artifactory.build-number").withValue(information.getBuildNumber()),
				arg("gpg.executable").withValue(gpg.getExecutable()), //
				arg("gpg.keyname").withValue(gpg.getKeyname()), //
				arg("gpg.passphrase").withValue(gpg.getPassphrase())) //
				.andIf(!ObjectUtils.isEmpty(properties.getSettingsXml()), settingsXml(properties.getSettingsXml()))
				.andIf(StringUtils.hasText(information.getProject()),
						() -> arg("artifactory.project").withValue(information.getProject()));

		mvn.execute(module.getSupportedProject(), arguments);
	}

	/**
	 * Triggers Maven commands to deploy to Sonatype's OSS Nexus if the given {@link ModuleIteration} refers to a version
	 * that has to be publicly released.
	 *
	 * @param module must not be {@literal null}.
	 * @param deploymentInformation must not be {@literal null}.
	 */
	private void doDeploy(ModuleIteration module, DeploymentInformation deploymentInformation) {

		Assert.notNull(module, "Module iteration must not be null!");
		Assert.notNull(deploymentInformation, "DeploymentInformation iteration must not be null!");

		if (module.isCommercial()) {
			logger.log(module, "Skipping deployment to Maven Central as it's a commercial release!");
			return;
		}

		StagingRepository stagingRepository = deploymentInformation.getStagingRepositoryId();

		if (stagingRepository.isPresent()) {
			logger.log(module, "Staging artifacts to %s…", stagingRepository);
		} else {
			logger.log(module, "Deploying artifacts Maven Central …");
		}

		Gpg gpg = getGpg();

		CommandLine arguments = CommandLine.of(Goal.CLEAN, Goal.DEPLOY, //
				profile("ci,release"), //
				SKIP_TESTS, //
				arg("gpg.executable").withValue(gpg.getExecutable()), //
				arg("gpg.keyname").withValue(gpg.getKeyname()), //
				arg("gpg.passphrase").withValue(gpg.getPassphrase())) //
				.andIf(!ObjectUtils.isEmpty(properties.getSettingsXml()), settingsXml(properties.getSettingsXml()))
				.andIf(stagingRepository.isPresent(), () -> arg("altDeploymentRepository").withValue(stagingRepository))
				.andIf(gpg.hasSecretKeyring(), () -> arg("gpg.secretKeyring").withValue(gpg.getSecretKeyring()));

		mvn.execute(module.getSupportedProject(), arguments);
	}

	@Override
	public void smokeTests(TrainIteration iteration, StagingRepository stagingRepository) {

		Assert.notNull(iteration, "Train iteration must not be null!");
		Assert.notNull(stagingRepository, "StagingRepository iteration must not be null!");

		logger.log(iteration, "🚬 Running smoke test…");

		boolean mavenCentral = iteration.isPublic();
		boolean commercial = iteration.isCommercial();
		String profile = commercial ? "commercial" : (mavenCentral ? "maven-central" : "artifactory");

		ModuleIteration module = iteration.getModule(BUILD);

		SupportedProject smokeTests = iteration.getSupportedProject(SMOKE_TESTS);
		doWithProjection(workspace.getFile(POM_XML, smokeTests), pom -> {

			Version version = module.getVersion();
			String targetBootVersion = version.getMajor() == 2 ? "2.7.8" : "3.0.2";

			pom.setParentVersion(ArtifactVersion.of(Version.parse(targetBootVersion), true));
		});

		CommandLine arguments = CommandLine.of(Goal.CLEAN, VERIFY, //
				profile(profile), //
				arg("s").withValue("settings.xml"), //
				arg("spring-data-bom.version").withValue(iteration.getReleaseTrainNameAndVersion())) //
				.andIf(mavenCentral, arg("deploymentId").withValue(stagingRepository.getId())) //
				.andIf(mavenCentral, arg("CENTRAL_BEARER").withValue(properties.getMavenCentral().getBearer()));

		mvn.execute(smokeTests, arguments);

		logger.log(iteration, "✅ Smoke tests passed. Do not smoke 🚭. It's unhealthy.");
	}

	@Override
	public <M extends ProjectAware> M triggerDocumentationBuild(M module) {

		SupportedProject project = module.getSupportedProject();

		mvn.execute(project, CommandLine.of(Goal.CLEAN, Goal.INSTALL, SKIP_TESTS, profile("distribute")));
		logger.log(project, "Successfully finished documentation build.");

		return module;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.build.BuildSystem#triggerDistributionBuild(org.springframework.data.release.model.Module)
	 */
	@Override
	public <M extends ProjectAware> M triggerDistributionBuild(M module) {

		Project project = module.getProject();

		if (BUILD.equals(project)) {
			return module;
		}

		if (BOM.equals(project)) {
			return module;
		}

		SupportedProject supportedProject = module.getSupportedProject();

		if (!isMavenProject(supportedProject)) {
			logger.log(project, "Skipping project as no pom.xml could be found in the working directory!");
			return module;
		}

		DefaultDeploymentInformation deploymentInformation = module instanceof ModuleIteration mi
				? new DefaultDeploymentInformation(mi, properties)
				: null;

		logger.log(project, "Triggering distribution build…");

		Authentication authentication = properties.getAuthentication(module);

		mvn.execute(supportedProject, CommandLine.of(Goal.CLEAN, Goal.DEPLOY, //
				SKIP_TESTS, profile("distribute"), Argument.of("-B"),
				arg("artifactory.server").withValue(authentication.getServer().getUri()),
				arg("artifactory.distribution-repository").withValue(authentication.getDistributionRepository()),
				arg("artifactory.username").withValue(authentication.getUsername()),
				arg("artifactory.password").withValue(authentication.getApiKey())).andIf(deploymentInformation != null, () -> {
					return arg("artifactory.build-number").withValue(deploymentInformation.getBuildNumber());
				}).andIf(!ObjectUtils.isEmpty(properties.getSettingsXml()), () -> settingsXml(properties.getSettingsXml())));

		mvn.execute(supportedProject, CommandLine.of(Goal.CLEAN, Goal.DEPLOY, //
				SKIP_TESTS, profile("distribute-schema"), Argument.of("-B"),
				arg("artifactory.server").withValue(authentication.getServer().getUri()),
				arg("artifactory.distribution-repository").withValue(authentication.getDistributionRepository()),
				arg("artifactory.username").withValue(authentication.getUsername()),
				arg("artifactory.password").withValue(authentication.getApiKey())).andIf(deploymentInformation != null, () -> {
					return arg("artifactory.build-number").withValue(deploymentInformation.getBuildNumber());
				}).andIf(!ObjectUtils.isEmpty(properties.getSettingsXml()), () -> settingsXml(properties.getSettingsXml())));

		logger.log(project, "Successfully finished distribution build!");

		return module;
	}

	private void updateBom(PomUpdater updater, UpdateInformation updateInformation, String file,
			SupportedProject project) {

		TrainIteration iteration = updateInformation.getTrain();

		logger.log(BUILD, "Updating BOM pom.xml…");

		doWithProjection(workspace.getFile(file, project), pom -> {

			for (ModuleIteration module : iteration.getModulesExcept(BUILD, BOM)) {

				ArtifactVersion version = updateInformation.getProjectVersionToSet(module.getProject());

				logger.log(project, "%s", module);

				String moduleArtifactId = new MavenArtifact(module).getArtifactId();
				pom.setDependencyManagementVersion(moduleArtifactId, version);
				logger.log(project, "Updated managed dependency version for %s to %s!", moduleArtifactId, version);

				module.getProject().doWithAdditionalArtifacts(additionalArtifact -> {

					String artifactId = additionalArtifact.getArtifactId();
					Artifact artifact = pom.getManagedDependency(artifactId);

					if (artifact != null) {
						pom.setDependencyManagementVersion(artifactId, version);
						logger.log(project, "Updated managed dependency version for %s to %s!", artifactId, version);
					} else {
						logger.log(project, "Artifact %s not found, skipping update!", artifactId);
					}
				});
			}

			if (updateInformation.getPhase().equals(Phase.PREPARE)) {

				// Make sure we have no snapshot leftovers
				List<Artifact> snapshotDependencies = pom.getSnapshotDependencies();

				if (!snapshotDependencies.isEmpty()) {
					throw new IllegalStateException("Found snapshot dependencies %s!".formatted(snapshotDependencies));
				}
			}

			updater.updateRepository(pom);
		});
	}

	private void updateParentPom(PomUpdater updater, UpdateInformation information) {

		// Fix version of shared resources to to-be-released version.
		doWithProjection(workspace.getFile("parent/pom.xml", information.getSupportedProject(BUILD)), ParentPom.class,
				pom -> {

					logger.log(BUILD, "Setting shared resources version to %s.", information.getParentVersionToSet());
					pom.setSharedResourcesVersion(information.getParentVersionToSet());

					logger.log(BUILD, "Setting releasetrain property to %s.", information.getReleaseTrainVersion());
					pom.setReleaseTrain(information.getReleaseTrainVersion());

					updater.updateRepository(pom);
				});
	}

	public boolean isMavenProject(ModuleIteration module) {

		if (!isMavenProject(module.getSupportedProject())) {
			logger.log(module, "No pom.xml file found, skipping project.");
			return false;
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.build.BuildSystem#verify()
	 */
	@Override
	public void verify(Train train) {

		logger.log(BUILD, "Verifying Maven Build System…");

		Gpg gpg = getGpg();

		CommandLine arguments = CommandLine.of(Goal.CLEAN, Goal.VERIFY, //
				profile("central"), //
				SKIP_TESTS, //
				arg("gpg.executable").withValue(gpg.getExecutable()), //
				arg("gpg.keyname").withValue(gpg.getKeyname()), //
				arg("gpg.passphrase").withValue(gpg.getPassphrase())) //
				.andIf(gpg.hasSecretKeyring(), () -> arg("gpg.secretKeyring").withValue(gpg.getSecretKeyring()));

		mvn.execute(train.getSupportedProject(BUILD), arguments);
	}

	@Override
	public void verifyStagingAuthentication(Train train) {

		if (train.isOpenSource()) {

			logger.log(BUILD, "Verifying Maven Staging Authentication…");

			return;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.plugin.core.Plugin#supports(java.lang.Object)
	 */
	@Override
	public boolean supports(SupportedProject project) {
		return isMavenProject(project);
	}

	private boolean isMavenProject(SupportedProject project) {
		return workspace.getFile(POM_XML, project).exists();
	}

	private void doWithProjection(File file, Consumer<Pom> callback) {
		doWithProjection(file, Pom.class, callback);
	}

	private Gpg getGpg() {

		MavenCentral mavenCentral = properties.getMavenCentral();

		if (mavenCentral.hasGpgConfiguration()) {
			return mavenCentral.getGpg();
		}

		return gpg;
	}

	/**
	 * TODO: Move XML file callbacks using the {@link ProjectionFactory} to {@link Workspace}.
	 */
	private <T extends Pom> void doWithProjection(File file, Class<T> type, Consumer<T> callback) {

		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
			byte[] content = doWithProjection((XBProjector) projectionFactory, bis, type, callback);

			try (FileOutputStream fos = new FileOutputStream(file)) {
				fos.write(content);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static <T extends Pom> byte[] doWithProjection(XBProjector projector, InputStream stream, Class<T> type,
			Consumer<T> callback) throws IOException {

		StreamInput io = projector.io().stream(stream);
		T pom = io.read(type);
		callback.accept(pom);

		StringWriter writer = new StringWriter();
		try {
			projector.config().createTransformer().transform(new DOMSource(((DOMAccess) pom).getDOMNode()),
					new StreamResult(writer));
		} catch (TransformerException e) {
			throw new RuntimeException(e);
		}

		String s = writer.toString();

		if (s.contains("standalone=\"no\"?><")) {
			s = s.replaceAll(Pattern.quote("standalone=\"no\"?><"), "standalone=\"no\"?>" + IOUtils.LINE_SEPARATOR + "<");
		}

		if (s.contains("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>")) {
			s = s.replaceAll(Pattern.quote("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"),
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		}

		if (s.contains("--><project ")) {
			s = s.replaceAll(Pattern.quote("--><project "), "-->" + IOUtils.LINE_SEPARATOR + "<project ");
		}

		s = s.replace(String.format("<repositories>%n\t\t%n\t</repositories>"),
				String.format("<repositories>%n\t</repositories>"));

		if (!s.endsWith(IOUtils.LINE_SEPARATOR)) {
			s += IOUtils.LINE_SEPARATOR;
		}

		return s.getBytes(StandardCharsets.UTF_8);
	}
}
