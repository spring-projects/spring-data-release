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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.issues.Tickets;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.Logger;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.table.Table;

/**
 * Shell commands for dependency management.
 *
 * @author Mark Paluch
 */
@CliComponent
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DependencyCommands extends TimedCommand {

	public static final String BUILD_PROPERTIES = "dependency-upgrade-build.properties";

	DependencyOperations operations;
	GitOperations git;
	Logger logger;

	@CliCommand(value = "dependency check")
	public void check(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "all", mandatory = false) Boolean reportAll,
			@CliOption(key = "project", mandatory = false) Project project) throws IOException {

		git.prepare(iteration);

		checkBuildDependencies(iteration, reportAll != null ? reportAll : false,
				it -> project == null || it.equals(project));
		checkModuleDependencies(iteration, reportAll != null ? reportAll : false,
				it -> project == null || it.equals(project));
	}

	/**
	 * Retrieve a dependency report for all store modules to be used typically in Spring Boot upgrade tickets.
	 *
	 * @param iteration
	 * @return
	 */
	@CliCommand(value = "dependency report")
	public String report(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		git.prepare(iteration);

		List<SupportedProject> projects = Projects.all().stream()
				.filter(it -> it != Projects.BOM && it != Projects.BUILD && it != Projects.COMMONS)
				.map(iteration::getSupportedProject).collect(Collectors.toList());

		Map<Dependency, DependencyVersion> dependencies = new TreeMap<>();

		for (SupportedProject project : projects) {
			operations.getCurrentDependencies(project).forEach(dependencies::put);
		}

		StringBuilder report = new StringBuilder();

		report.append(System.lineSeparator()).append("Project Dependencies Spring Data ")
				.append(iteration.getReleaseTrainNameAndVersion()).append(System.lineSeparator())
				.append(System.lineSeparator());

		dependencies.forEach((dependency, dependencyVersion) -> {

			report.append(String.format("* %s (%s:%s): %s", dependency.getName(), dependency.getGroupId(),
					dependency.getArtifactId(), dependencyVersion.getIdentifier())).append(System.lineSeparator());
		});

		return report.toString();
	}

	@CliCommand(value = "dependency upgrade")
	public void upgrade(@CliOption(key = "", mandatory = true) TrainIteration iteration)
			throws IOException, InterruptedException {

		logger.log(iteration, "Applying dependency upgrades to Spring Data Build");

		ModuleIteration module = iteration.getModule(Projects.BUILD);
		DependencyVersions dependencyVersions = loadDependencyUpgrades(module);

		DependencyVersions upgradesToApply = operations.getDependencyUpgradesToApply(module.getSupportedProject(),
				dependencyVersions);

		if (upgradesToApply.isEmpty()) {
			logger.log(module, "No dependency upgrades to apply");
			return;
		}

		Tickets tickets = operations.getOrCreateUpgradeTickets(module, upgradesToApply);
		operations.upgradeDependencies(tickets, module, dependencyVersions);

		git.push(module);

		// Allow GitHub to catch up with ticket notifications.
		Thread.sleep(1500);

		operations.closeUpgradeTickets(module, tickets);
	}

	private DependencyVersions loadDependencyUpgrades(ModuleIteration iteration) throws IOException {

		if (!Files.exists(Path.of(BUILD_PROPERTIES))) {
			logger.log(iteration, "Cannot upgrade dependencies: " + BUILD_PROPERTIES + " does not exist.");
		}

		Properties properties = new Properties();
		try (FileInputStream fis = new FileInputStream(BUILD_PROPERTIES)) {
			properties.load(fis);
		}

		return DependencyUpgradeProposals.fromProperties(iteration.getTrainIteration(), properties);
	}

	private void checkModuleDependencies(TrainIteration iteration, boolean reportAll, Predicate<Project> projectFilter)
			throws IOException {

		String propertiesFile = "dependency-upgrade-modules.properties";

		List<SupportedProject> projects = Projects.all().stream() //
				.filter(it -> it != Projects.BOM && it != Projects.BUILD) //
				.filter(projectFilter) //
				.map(iteration::getSupportedProject) //
				.collect(Collectors.toList());

		DependencyUpgradeProposals proposals = DependencyUpgradeProposals.empty();

		for (SupportedProject project : projects) {
			proposals = proposals.mergeWith(operations.getDependencyUpgradeProposals(project, iteration.getIteration()));
		}

		Files.write(Path.of(propertiesFile), proposals.asProperties(iteration).getBytes());

		Table summary = proposals.toTable(reportAll);

		logger.log(iteration, "Upgrade summary:" + System.lineSeparator() + System.lineSeparator() + summary);
		logger.log(iteration, "Upgrade proposals written to " + propertiesFile);
	}

	private void checkBuildDependencies(TrainIteration iteration, boolean reportAll, Predicate<Project> projectFilter)
			throws IOException {

		String propertiesFile = BUILD_PROPERTIES;

		if (!projectFilter.test(Projects.BUILD)) {
			return;
		}

		SupportedProject project = iteration.getSupportedProject(Projects.BUILD);
		DependencyUpgradeProposals proposals = operations.getDependencyUpgradeProposals(project, iteration.getIteration());

		Files.write(Path.of(propertiesFile), proposals.asProperties(iteration).getBytes());

		Table summary = proposals.toTable(reportAll);

		logger.log(Projects.BUILD, "Upgrade summary:" + System.lineSeparator() + System.lineSeparator() + summary);
		logger.log(iteration, "Upgrade proposals written to " + propertiesFile);
	}

}
