/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.data.release.infra;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.git.Branch;
import org.springframework.data.release.git.BranchMapping;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.issues.Tickets;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.ProjectMaintainer;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.release.utils.Logger;
import org.springframework.data.util.Predicates;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;

/**
 * @author Mark Paluch
 * @author Christoph Strobl
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InfrastructureOperations extends TimedCommand {

	public static final String CONFIG_JSON = "config.json";

	public static final String MAVEN_PROPERTIES = "dependency-upgrade-maven.properties";

	static final Pattern GH_ACTION_REF_PATTERN = Pattern
			.compile("(?<=uses: spring-projects/spring-data-build/actions/[\\w-]+)@[\\w./-]+");

	static final Pattern GH_ACTION_NAME_PATTERN = Pattern.compile("name:(.*CI.*)");

	static final Pattern GH_ACTION_PUSH_BRANCHES_PATTERN = Pattern
			.compile("branches:(.*\\[.*\\s*\\])");

	DependencyOperations dependencies;
	ReadmeProcessor readmeProcessor;
	Workspace workspace;
	GitOperations git;
	ExecutorService executor;
	Logger logger;

	/**
	 * Distribute build config from {@link Projects#BUILD} to all modules within {@link TrainIteration}.
	 *
	 * @param iteration
	 */
	void distributeCiProperties(TrainIteration iteration) {
		distributeFiles(iteration, Arrays.asList(".mvn/extensions.xml", ".mvn/jvm.config"), "Maven Build Config",
				Predicates.isTrue());
	}

	void distribute(TrainIteration iteration, String file, String commitMessage) {
		distributeFiles(iteration, Arrays.asList(file), commitMessage, Predicates.isTrue());
	}

	void distributeGhWorkflow(TrainIteration iteration) {
		distributeFiles(iteration, List.of(".github/workflows/project.yml", ".github/workflows/codeql.yml"),
				"GitHub Actions",
				project -> project != Projects.BOM && project.getMaintainer() == ProjectMaintainer.CORE);
	}

	private void distributeFiles(TrainIteration iteration, List<String> files, String description,
			Predicate<Project> projectFilter) {

		ExecutionUtils.run(executor, iteration, module -> {

			SupportedProject project = module.getSupportedProject();
			Branch branch = Branch.from(module);

			git.update(project);
			git.checkout(project, branch);
		});

		Streamable<ModuleIteration> projects = Streamable.of(iteration.getModulesExcept(Projects.BUILD))
				.filter(it -> projectFilter.test(it.getProject()));

		for (String file : files) {
			verifyExistingFiles(Streamable.of(iteration.getModule(Projects.BUILD)), file, description);
		}

		ExecutionUtils.run(executor, projects, module -> {

			for (String file : files) {

				File master = workspace.getFile(file, iteration.getSupportedProject(Projects.BUILD));
				File target = workspace.getFile(file, module.getSupportedProject());
				target.delete();

				FileUtils.copyFile(master, target);
				git.add(module.getSupportedProject(), file);
			}

			git.commit(module, String.format("Update %s.", description), Optional.empty(), false);
			git.push(module);
		});
	}

	public void updateGhActionsConfig(TrainIteration iteration) {

		ExecutionUtils.run(executor, iteration, module -> {

			BranchMapping branchMapping = new BranchMapping();
			Branch buildBranch = Branch.from(iteration.getModule(Projects.BUILD));
			branchMapping.add(Projects.BUILD, buildBranch, buildBranch);
			branchMapping.add(module.getProject(), Branch.from(module), Branch.from(module));
			updateGhActionsConfig(module, branchMapping);

			git.commit(module, "Update GitHub action branch triggers.", Optional.empty(), false);
			git.push(module);
		});

	}

	/**
	 * Updates GitHub Actions YAML files to reference the new branch. This includes both public GitHub Actions located in
	 * the [actions] folder and workflow YAML files located in [.github/workflows].
	 *
	 * @param module
	 * @param branches
	 * @return
	 */
	public ModuleIteration updateGhActionsConfig(ModuleIteration module, BranchMapping branches) {

		Branch targetBranch = branches.getTargetBranch(module.getProject());
		if (targetBranch == null) {
			logger.warn(module, "No target branch available, skipping GH action config update.");
			return module;
		}

		/* check if we have the build module in hand
		 * if so we need to update the public GitHub Actions located in the [actions] folder.
		 * each action is located in a subfolder and named [action.yml] or [action.yaml]. */
		List<String> actionFileCandidates = List.of("action.yml", "action.yaml");
		if (module.getProject().equals(Projects.BUILD)) {

			File ghActionsDirectory = workspace.getFile("actions", module.getSupportedProject());
			if (ghActionsDirectory.isDirectory()) {

				Collection<File> actions = FileUtils.listFiles(ghActionsDirectory, new NameFileFilter(actionFileCandidates),
						TrueFileFilter.INSTANCE);
				for (File actionFile : actions) {
					if (isYamlFile(actionFile)) {
						if (updateActionBranch(module, actionFile, targetBranch, targetBranch)) {
							git.add(module.getSupportedProject(), actionFile);
						}
					}
				}
			}
		}

		/* Github workflow YAML files located in .github/workflows need to be updated.
		 * There is no need to update 'local' repository Github actions as those can only be referenced from
		 * within a specific branch. */
		File workflows = workspace.getFile(".github/workflows", module.getSupportedProject());
		if (!workflows.isDirectory()) {
			logger.log(module, "No GH Action workflows found, skipping config update.");
			return module;
		}

		Collection<File> workflowFiles = FileUtils.listFiles(workflows, new SuffixFileFilter(".yml", ".yaml"),
				TrueFileFilter.INSTANCE);

		for (File workflowFile : workflowFiles) {
			if (!isYamlFile(workflowFile) || workflowFile.getName().contains("project.yml")
					|| workflowFile.getName().contains("codeql.yml")) {
				continue;
			}

			Branch buildBranch = branches.getTargetBranch(Projects.BUILD);
			if (updateActionBranch(module, workflowFile, targetBranch, buildBranch)) {
				git.add(module.getSupportedProject(), workflowFile);
			}
		}

		return module;
	}

	/**
	 * @param moduleIteration
	 * @param ghActionFile
	 * @param branch branch of the actual project.
	 * @param buildBranch branch used by Spring Data Build.
	 */
	boolean updateActionBranch(ModuleIteration moduleIteration, File ghActionFile, Branch branch, Branch buildBranch) {

		if (!ghActionFile.isFile()) {
			logger.log(moduleIteration, "Not a GH action file [%s]. Skipping branch update.", ghActionFile.getPath());
			return false;
		}

		try {

			byte[] bytes = Files.readAllBytes(ghActionFile.toPath());
			String content = new String(bytes, StandardCharsets.UTF_8);

			String newContent = updateActionRefs(content, buildBranch);
			newContent = updateOnPushBranches(newContent, branch);

			if (ghActionFile.getName().contains("ci.y")) {
				newContent = updateActionName(newContent, "CI " + branch);
			}

			if (!newContent.equals(content)) {
				logger.log(moduleIteration, "GH action file [%s] updated.".formatted(ghActionFile.getPath()));
				Files.writeString(ghActionFile.toPath(), newContent);
				return true;
			} else {
				logger.log(moduleIteration, "GH action file [%s] unchanged. Skipping.".formatted(ghActionFile.getPath()));
			}
		} catch (IOException e) {
			logger.warn(moduleIteration, "GH action file [%s] update failed.", ghActionFile.getPath());
		}

		return false;
	}

	/**
	 * @param file must not be {@literal null}.
	 * @return true if is a file that ends with either {@code .yml} or {@code .yaml}.
	 */
	private static boolean isYamlFile(File file) {
		return file.isFile() && (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml"));
	}

	/**
	 * Replaces {@code uses} sections within GitHub Actions YAML string that points to spring-data-build with the given
	 * {@literal branch} version.
	 *
	 * @param yaml the full YAML content
	 * @param branch the new branch (e.g. {@code "5.1.x"})
	 * @return never {@literal null}.
	 */
	static String updateActionRefs(String yaml, Branch branch) {
		return GH_ACTION_REF_PATTERN.matcher(yaml).replaceAll(Matcher.quoteReplacement("@" + branch));
	}

	/**
	 * Replaces the main branch and {@code 'issue/**'} pattern in the {@code branches} section of a GitHub Actions
	 * workflow YAML string and removes any intermediate branches
	 *
	 * @param yaml the full YAML content.
	 * @param branch the new branch (e.g. {@code "5.1.x"}).
	 * @return the content with {@code main} and {@code 'issue/**'} replaced by {@code newBranchName} and
	 *         {@code 'issue/newBranchName/**'}, and any intermediate branches removed
	 */
	static String updateOnPushBranches(String yaml, Branch branch) {

		Matcher matcher = GH_ACTION_PUSH_BRANCHES_PATTERN.matcher(yaml);
		String quoted = Matcher.quoteReplacement(branch.toString());

		if (matcher.find()) {

			String group = matcher.group(1);
			String onBranches;
			if (group.contains("issue/")) {
				if (Branch.MAIN.equals(branch)) {
					onBranches = "branches: [ " + quoted + ", 'issue/**' ]";
				} else {
					onBranches = "branches: [ " + quoted + ", 'issue/" + quoted + "/**' ]";
				}
			} else {
				onBranches = "branches: [ " + quoted + " ]";
			}

			return matcher.replaceAll(onBranches);
		}

		return yaml;
	}

	/**
	 * Update the name of a GitHub Action if it contains "CI" to reflect the new branch name.
	 *
	 * @param yaml the full YAML content
	 * @param name the name to use.
	 * @return updated YAML content.
	 */
	static String updateActionName(String yaml, String name) {

		Matcher matcher = GH_ACTION_NAME_PATTERN.matcher(yaml);
		String quoted = Matcher.quoteReplacement(name);
		return matcher.replaceAll("name: " + quoted);

	}

	private void verifyExistingFiles(Streamable<ModuleIteration> train, String file, String description) {

		for (ModuleIteration moduleIteration : train) {

			File target = workspace.getFile(file, moduleIteration.getSupportedProject());

			if (!target.exists()) {
				throw new IllegalStateException(
						String.format("%s file %s does not exist in %s", description, file, moduleIteration.getSupportedProject()));
			}
		}
	}

	public void upgradeMavenVersion(TrainIteration iteration) {

		DependencyVersions dependencyVersions = dependencies.loadDependencyUpgrades(iteration, MAVEN_PROPERTIES);

		if (dependencyVersions.isEmpty()) {
			throw new IllegalStateException("No version to upgrade found!");
		}

		List<Project> projectsToUpgrade = dependencies
				.getProjectsToUpgradeMavenWrapper(dependencyVersions.get(Dependencies.MAVEN), iteration);

		ExecutionUtils.run(executor, Streamable.of(projectsToUpgrade), project -> {

			ModuleIteration module = iteration.getModule(project);
			Tickets tickets = dependencies.getOrCreateUpgradeTickets(module, dependencyVersions);
			dependencies.upgradeMavenWrapperVersion(tickets, module, dependencyVersions);
			git.push(module);

			// Allow GitHub to catch up with ticket notifications.
			Thread.sleep(1500);

			dependencies.closeUpgradeTickets(module, tickets);
		});
	}

	public void generateReadmes(TrainIteration iteration) {

		ExecutionUtils.run(executor, iteration, it -> {

			File template = workspace.getFile(".github/README.template.adoc", it.getSupportedProject());

			if (template.exists()) {

				String result = readmeProcessor.preprocess(template, it);
				File target = workspace.getFile("README.adoc", it.getSupportedProject());

				FileUtils.write(target, result, StandardCharsets.UTF_8);

				git.add(it.getSupportedProject(), "README.adoc");
				git.commit(it, "Update Readme.", Optional.empty(), false);
				git.push(it);
			}
		});
	}

}
