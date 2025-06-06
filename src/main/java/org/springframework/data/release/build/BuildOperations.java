/*
 * Copyright 2016-2022 the original author or authors.
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

import static org.springframework.data.release.model.Projects.*;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.assertj.core.util.VisibleForTesting;

import org.springframework.data.release.deployment.DeploymentInformation;
import org.springframework.data.release.deployment.MavenPublisher;
import org.springframework.data.release.deployment.StagingRepository;
import org.springframework.data.release.git.BranchMapping;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Phase;
import org.springframework.data.release.model.ProjectAware;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.Logger;
import org.springframework.data.util.Streamable;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 * @author Greg Turnquist
 */
@Component
@RequiredArgsConstructor
public class BuildOperations {

	private final @NonNull PluginRegistry<BuildSystem, SupportedProject> buildSystems;
	private final @NonNull Logger logger;
	private final @NonNull MavenProperties properties;
	private final @NonNull BuildExecutor executor;
	private final @NonNull MavenPublisher publisher;
	private final Workspace workspace;

	/**
	 * Updates all inter-project dependencies based on the given {@link TrainIteration} and release {@link Phase}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param phase must not be {@literal null}.
	 * @throws Exception
	 */
	public void updateProjectDescriptors(TrainIteration iteration, Phase phase) throws Exception {

		Assert.notNull(iteration, "Train iteration must not be null!");
		Assert.notNull(phase, "Phase must not be null!");

		UpdateInformation updateInformation = UpdateInformation.of(iteration, phase);

		BuildExecutor.Summary<ModuleIteration> summary = executor.doWithBuildSystemOrdered(iteration,
				(system, it) -> system.updateProjectDescriptors(it, updateInformation));

		logger.log(iteration, "Update Project Descriptors done: %s", summary);
	}

	public void updateBuildConfig(TrainIteration iteration, BranchMapping branches) throws Exception {

		Assert.notNull(iteration, "Train iteration must not be null!");

		BuildExecutor.Summary<ModuleIteration> summary = executor.doWithBuildSystemOrdered(iteration,
				(system, it) -> system.updateBuildConfig(it, branches));

		logger.log(iteration, "Update Build config done: %s", summary);
	}

	/**
	 * Prepares the versions of the given {@link TrainIteration} depending on the given {@link Phase}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param phase must not be {@literal null}.
	 */
	public void prepareVersions(TrainIteration iteration, Phase phase) {

		Assert.notNull(iteration, "Train iteration must not be null!");
		Assert.notNull(phase, "Phase must not be null!");

		BuildExecutor.Summary<ModuleIteration> summary = executor.doWithBuildSystemOrdered(iteration,
				(system, module) -> system.prepareVersion(module, phase));

		logger.log(iteration, "Prepare versions: %s", summary);
	}

	/**
	 * Prepares the version of the given {@link ModuleIteration} depending on the given {@link Phase}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param phase must not be {@literal null}.
	 * @return
	 */
	@VisibleForTesting
	public ModuleIteration prepareVersion(ModuleIteration iteration, Phase phase) {

		Assert.notNull(iteration, "Module iteration must not be null!");
		Assert.notNull(phase, "Phase must not be null!");

		return doWithBuildSystem(iteration, (system, module) -> system.prepareVersion(module, phase));
	}

	/**
	 * Performs a local build for all modules in the given {@link TrainIteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @return
	 */
	public void build(TrainIteration iteration) {

		executor.doWithBuildSystemOrdered(iteration, (it, l) -> it.triggerBuild(l));

		logger.log(iteration, "Build finished");
	}

	/**
	 * Promote the staging repository by publishing the deployment.
	 *
	 * @param iteration
	 * @param stagingRepository must not be {@literal null}.
	 * @return
	 */
	public void publishDeployment(TrainIteration iteration, StagingRepository stagingRepository) {
		publisher.publish(iteration, stagingRepository);
	}

	/**
	 * Run smoke tests for a {@link TrainIteration} against a {@link StagingRepository}.
	 *
	 * @param iteration
	 * @param stagingRepository
	 */
	public void smokeTests(TrainIteration iteration, StagingRepository stagingRepository) {

		doWithBuildSystem(iteration.getModule(BUILD), (buildSystem, moduleIteration) -> {
			buildSystem.smokeTests(iteration, stagingRepository);
			return null;
		});
	}

	public void buildDocumentation(TrainIteration iteration) {

		Streamable<ModuleIteration> of = Streamable.of(iteration.getModulesExcept(BOM, COMMONS, BUILD));

		executor.doWithBuildSystemOrdered(of, BuildSystem::triggerDocumentationBuild);

		logger.log(iteration, "Documentation build finished");
	}

	public void buildDocumentation(ModuleIteration iteration) {

		doWithBuildSystem(iteration, BuildSystem::triggerDocumentationBuild);

		logger.log(iteration, "Documentation build finished");
	}

	/**
	 * Performs the release build for all modules in the given {@link TrainIteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @return
	 */
	public List<DeploymentInformation> performRelease(TrainIteration iteration) {

		StagingRepository localStaging = iteration.isPublic() ? initializeStagingRepository() : StagingRepository.EMPTY;
		StagingRepository stagingRepository = StagingRepository.EMPTY;

		BuildExecutor.Summary<DeploymentInformation> summary = executor.doWithBuildSystemOrdered(iteration,
				(buildSystem, moduleIteration) -> buildSystem.deploy(moduleIteration, localStaging));

		if (iteration.isPublic()) {
			stagingRepository = uploadDeployment(iteration.getModule(BOM), localStaging);
		}

		smokeTests(iteration, stagingRepository);

		logger.log(iteration, "Release: %s", summary);

		if (stagingRepository.isPresent()) {
			publishDeployment(iteration, stagingRepository);
		}

		return summary.getExecutions().stream().map(BuildExecutor.ExecutionResult::getResult).collect(Collectors.toList());
	}

	/**
	 * Performs the staging build for all modules in the given {@link TrainIteration} without deploying artifacts
	 * remotely.
	 *
	 * @param iteration must not be {@literal null}.
	 * @return
	 */
	public List<DeploymentInformation> stageRelease(TrainIteration iteration) {

		StagingRepository localStaging = initializeStagingRepository();

		BuildExecutor.Summary<DeploymentInformation> summary = executor.doWithBuildSystemOrdered(iteration,
				(buildSystem, moduleIteration) -> buildSystem.deploy(moduleIteration, localStaging));

		logger.log(iteration, "Release: %s", summary);

		return summary.getExecutions().stream().map(BuildExecutor.ExecutionResult::getResult).collect(Collectors.toList());
	}

	/**
	 * Performs the staging build for {@link ModuleIteration} without deploying artifacts remotely.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	public DeploymentInformation stageRelease(ModuleIteration module) {

		StagingRepository localStaging = initializeStagingRepository();

		return doWithBuildSystem(module,
				(buildSystem, moduleIteration) -> buildSystem.deploy(moduleIteration, localStaging));
	}

	public void uploadDeployment(TrainIteration iteration) {

		StagingRepository localStaging = publisher.getStagingRepository();
		StagingRepository stagingRepository = uploadDeployment(iteration.getModule(BOM), localStaging);

		logger.log(iteration, "Created deployment: %s", stagingRepository);
	}

	@SneakyThrows
	public void validateDeployment(TrainIteration iteration, StagingRepository deploymentId) {

		MavenPublisher.DeploymentStatus status = publisher.validate(iteration, deploymentId);
		logger.log(iteration, "Deployment validated: %s, \n%s", status.getDeploymentState(),
				status.getPurls().stream().map(it -> "    * " + it).collect(Collectors.joining(System.lineSeparator())));
	}

	@SneakyThrows
	private StagingRepository uploadDeployment(ModuleIteration iteration, StagingRepository localStaging) {

		String deploymentName;

		if (iteration.getProject() == BOM) {
			deploymentName = String.format("Spring Data %s", iteration.getTrainIteration().getName());
		} else {
			deploymentName = String.format("Spring Data %s", iteration);
		}

		StagingRepository deploymentId = publisher.upload(iteration, deploymentName, localStaging);
		publisher.validate(iteration.getTrainIteration(), deploymentId);

		return deploymentId;
	}

	@SneakyThrows
	private StagingRepository initializeStagingRepository() {
		return publisher.initializeStagingRepository();
	}

	/**
	 * Performs the release build for the given {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	public DeploymentInformation performRelease(ModuleIteration module) {
		return buildAndDeployRelease(module);
	}

	/**
	 * Triggers the distribution builds for all modules participating in the given {@link TrainIteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 */
	public void distributeResources(TrainIteration iteration) {

		Assert.notNull(iteration, "Train iteration must not be null!");

		BuildExecutor.Summary<?> summary = executor.doWithBuildSystemAnyOrder(iteration,
				BuildSystem::triggerDistributionBuild);

		logger.log(iteration, "Distribution build: %s", summary);
	}

	/**
	 * Triggers the distribution builds for all modules participating in the given {@link Train}.
	 *
	 * @param train must not be {@literal null}.
	 */
	public void distributeResources(Train train) {

		Assert.notNull(train, "Train must not be null!");

		BuildExecutor.Summary<?> summary = executor.doWithBuildSystemAnyOrder(train, BuildSystem::triggerDistributionBuild);

		logger.log(train, "Distribution build: %s", summary);
	}

	/**
	 * Triggers the distribution builds for the given module.
	 *
	 * @param iteration must not be {@literal null}.
	 */
	public void distributeResources(ModuleIteration iteration) {

		Assert.notNull(iteration, "ModuleIteration must not be null!");

		doWithBuildSystem(iteration, BuildSystem::triggerDistributionBuild);
	}

	/**
	 * Returns the {@link Path} of the local artifact repository.
	 *
	 * @return
	 */
	public Path getLocalRepository() {
		return properties.getLocalRepository().toPath();
	}

	/**
	 * Builds the release for the given {@link ModuleIteration} and deploys it to the staging repository.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	public DeploymentInformation buildAndDeployRelease(ModuleIteration module) {
		return doWithBuildSystem(module, BuildSystem::deploy);
	}

	/**
	 * Triggers a normal build for the given {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	public ModuleIteration triggerBuild(ModuleIteration module) {
		return doWithBuildSystem(module, BuildSystem::triggerBuild);
	}

	/**
	 * Triggers the pre-release checks for all modules of the given {@link TrainIteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 */
	public void runPreReleaseChecks(TrainIteration iteration) {

		Assert.notNull(iteration, "Train iteration must not be null!");

		executor.doWithBuildSystemAnyOrder(iteration, BuildSystem::triggerPreReleaseCheck);
	}

	/**
	 * Verifies Java version presence and that the project can be build using Maven.
	 *
	 * @param train must not be {@literal null}.
	 */
	public void verify(Train train) {

		SupportedProject project = train.getSupportedProject(Projects.BUILD);
		BuildSystem buildSystem = buildSystems.getRequiredPluginFor(project);

		buildSystem.withJavaVersion(executor.detectJavaVersion(project)).verify(train);
	}

	/**
	 * Verifies Maven staging authentication.
	 *
	 * @param train must not be {@literal null}.
	 */
	public void verifyStagingAuthentication(Train train) {

		SupportedProject project = train.getSupportedProject(Projects.BUILD);
		BuildSystem buildSystem = buildSystems.getRequiredPluginFor(project);

		buildSystem.withJavaVersion(executor.detectJavaVersion(project)).verifyStagingAuthentication(train);
	}

	/**
	 * Selects the build system for the module contained in the given {@link ModuleIteration} and executes the given
	 * function with it.
	 *
	 * @param module must not be {@literal null}.
	 * @param function must not be {@literal null}.
	 * @return
	 */
	private <T, S extends ProjectAware> T doWithBuildSystem(S module, BiFunction<BuildSystem, S, T> function) {

		Assert.notNull(module, "ModuleIteration must not be null!");

		Supplier<IllegalStateException> exception = () -> new IllegalStateException(
				String.format("No build system plugin found for project %s!", module.getSupportedProject()));

		BuildSystem buildSystem = buildSystems.getPluginFor(module.getSupportedProject(), exception);

		return function.apply(buildSystem.withJavaVersion(executor.detectJavaVersion(module.getSupportedProject())),
				module);
	}

}
