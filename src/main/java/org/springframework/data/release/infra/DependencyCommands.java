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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.issues.Tickets;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.SupportStatus;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.ExecutionUtils;
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
	public static final String MODULE_PROPERTIES = "dependency-upgrade-modules.properties";

	DependencyOperations operations;
	ExecutorService executor;
	GitOperations git;
	Logger logger;

	@CliCommand(value = "dependency check")
	public void check(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "all", mandatory = false) Boolean reportAll,
			@CliOption(key = "project", mandatory = false) Project project) throws IOException {

		git.prepare(iteration);

		createDependencyUpgradeProposals(iteration, reportAll != null ? reportAll : false,
				it -> (it.equals(Projects.BUILD) && (project == null || it.equals(project))), BUILD_PROPERTIES);
		createDependencyUpgradeProposals(iteration, reportAll != null ? reportAll : false,
				it -> (!it.equals(Projects.BUILD) && !it.equals(Projects.BOM) && (project == null || it.equals(project))),
				MODULE_PROPERTIES);
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

		Map<Project, DependencyVersions> upgradeVersions = new LinkedHashMap<>();

		if (operations.hasDependencyUpgrades(BUILD_PROPERTIES)) {
			upgradeVersions.put(Projects.BUILD, operations.loadDependencyUpgrades(iteration, BUILD_PROPERTIES));
		}

		if (operations.hasDependencyUpgrades(MODULE_PROPERTIES)) {
			DependencyVersions module = operations.loadDependencyUpgrades(iteration, MODULE_PROPERTIES);
			Projects.all().stream().filter(it -> it != Projects.BOM && it != Projects.BUILD).forEach(project -> {
				upgradeVersions.put(project, module);
			});
		}

		if (upgradeVersions.isEmpty()) {
			logger.log(iteration, "No dependency upgrades to apply");
			return;
		}

		ExecutionUtils.run(executor, iteration, module -> {

			if (!upgradeVersions.containsKey(module.getProject())) {
				return;
			}

			DependencyVersions upgrades = upgradeVersions.get(module.getProject());
			DependencyVersions upgradesToApply = operations.getDependencyUpgradesToApply(module.getSupportedProject(),
					upgrades);

			if (upgradesToApply.isEmpty()) {
				return;
			}

			Tickets tickets = operations.getOrCreateUpgradeTickets(module, upgradesToApply);
			operations.upgradeDependencies(tickets, module, upgradesToApply);

			git.push(module);

			// Allow GitHub to catch up with ticket notifications.
			Thread.sleep(1500);

			operations.closeUpgradeTickets(module, tickets);
			logger.log(module, "Upgraded %d dependencies", upgradesToApply.getDependencies().size());
		});
	}

	@CliCommand(value = "dependency tickets create")
	public Tickets createDependencyUpgradeTickets(
			@CliOption(key = "status", unspecifiedDefaultValue = "OSS") SupportStatus supportStatus,
			@CliOption(key = "trains", unspecifiedDefaultValue = "3") String trainNames,
			@CliOption(key = "dryRun", unspecifiedDefaultValue = "false", specifiedDefaultValue = "true") boolean dryRun) {
		List<Train> latest = ReleaseTrains.getTrains(trainNames, 3).stream()
				.filter(it -> it.getSupportStatus().equals(supportStatus)).toList();
		return operations.createDependencyUpgradeTicketsForScheduledReleases(supportStatus, latest, dryRun);
	}

	private void createDependencyUpgradeProposals(TrainIteration iteration, boolean reportAll,
			Predicate<Project> projectFilter, String propertiesFile)
			throws IOException {

		List<SupportedProject> projects = iteration.stream() //
				.map(ModuleIteration::getSupportedProject) //
				.filter(it -> projectFilter.test(it.getProject())) //
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

}
