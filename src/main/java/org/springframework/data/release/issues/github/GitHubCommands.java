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
package org.springframework.data.release.issues.github;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.issues.IssueTracker;
import org.springframework.data.release.issues.TicketReference;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.SupportStatus;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Tracker;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.util.Streamable;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;

/**
 * Component to execute GitHub related operations.
 *
 * @author Mark Paluch
 */
@Slf4j
@CliComponent
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GitHubCommands extends TimedCommand {

	private static final DateTimeFormatter DUE_ON_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	@NonNull PluginRegistry<IssueTracker, SupportedProject> tracker;
	@Getter
	@NonNull GitHub gitHub;
	@NonNull GitOperations git;
	@NonNull GitHubLabels gitHubLabels;
	@NonNull Executor executor;

	@CliCommand(value = "github update labels")
	public void createOrUpdateLabels(@CliOption(key = "project", mandatory = false) Project project,
			@CliOption(key = "commercial", mandatory = false) Boolean commercial) {

		SupportStatus status = commercial == null || !commercial ? SupportStatus.OSS : SupportStatus.COMMERCIAL;

		if (project == null) {
			ExecutionUtils.run(executor, Streamable.of(Projects.all(status)),
					it -> gitHubLabels.createOrUpdateLabels(SupportedProject.of(it, status)));
			return;
		}

		gitHubLabels.createOrUpdateLabels(SupportedProject.of(project, status));
	}

	@CliCommand(value = "github push")
	public void push(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		retry(() -> {
			git.push(iteration);
			git.pushTags(iteration.getTrain());

			if (!iteration.getTrain().isAlwaysUseBranch() && iteration.getIteration().isGAIteration()) {
				git.push(new TrainIteration(iteration.getTrain(), Iteration.SR1));
			}

			createOrUpdateRelease(iteration, null);
		}, 2);
	}

	@CliCommand(value = "github release create")
	public void createOrUpdateRelease(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "project") Project project) {

		TrainIteration previousIteration = git.getPreviousIteration(iteration);

		if (project != null) {

			ModuleIteration module = iteration.getModule(project);
			createOrUpdateRelease(module, previousIteration);
			return;
		}

		ExecutionUtils.run(executor, iteration, it -> {

			if (it.getSupportedProject().getProject().getTracker() == Tracker.GITHUB) {
				createOrUpdateRelease(it, previousIteration);
			}
		});
	}

	@CliCommand(value = "github release preview")
	public String previewRelease(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "project", mandatory = true) Project project) {

		TrainIteration previousIteration = git.getPreviousIteration(iteration);
		ModuleIteration module = iteration.getModule(project);

		List<TicketReference> ticketReferences = git.getTicketReferencesBetween(module.getSupportedProject(),
				previousIteration, module.getTrainIteration());

		return gitHub.createReleaseMarkdown(module, ticketReferences);
	}

	@CliCommand(value = "github milestone list")
	public String listOpenMilestones(
			@CliOption(key = "", mandatory = false, unspecifiedDefaultValue = "OSS") SupportStatus supportStatus) {

		List<Milestone> milestones = gitHub.listOpenMilestones(supportStatus);
		return render(supportStatus, milestones);
	}

	@CliCommand(value = "github milestone verify")
	public void verifyScheduledRelease(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		List<Milestone> milestones = gitHub.listOpenMilestones(iteration.getSupportStatus());

		String calver = iteration.getModuleVersion(Projects.BOM).toString();
		for (Milestone milestone : milestones) {
			if (milestone.isReleaseSoon() && milestone.getTitle().equals(calver)) {
				return;
			}
		}

		throw new IllegalStateException("No scheduled milestone found for " + calver + ". Scheduled milestones:\n"
				+ render(iteration.getSupportStatus(), milestones));
	}

	private static String render(SupportStatus supportStatus, List<Milestone> milestones) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Next %s Milestones:".formatted(supportStatus.name())).append(System.lineSeparator());
		for (Milestone milestone : milestones) {
			buffer.append("\t * %-14s %s".formatted(milestone.getTitle(), format(milestone))).append(System.lineSeparator());
		}
		return buffer.toString();
	}

	private static String format(Milestone milestone) {

		if (milestone.getDueOn() == null) {
			return "⚠️ (unscheduled)";
		}

		String formatted = DUE_ON_FORMATTER.format(milestone.getDueOn().atZone(ZoneId.systemDefault()));
		return milestone.isReleaseSoon() ? "⏰️ " + formatted : formatted;
	}

	@CliCommand(value = "github wiki status-matrix")
	public String createBuildStatusMatrix(@CliOption(key = "trains") String trainNames) {

		List<Project> projects = new ArrayList<>(15);
		projects.add(Projects.BOM);
		projects.addAll(Projects.all());
		projects.sort(Comparator.comparing(it -> it.getName().toLowerCase(Locale.ROOT)));
		List<Train> trains = getTrains(trainNames);

		SupportStatus supportStatus = SupportStatus.OSS;

		for (Train train : trains) {
			if (train.isCommercial()) {
				supportStatus = SupportStatus.COMMERCIAL;
				break;
			}
		}

		StringBuilder builder = new StringBuilder();

		for (Project project : projects) {

			GitProject gitProject = GitProject.of(SupportedProject.of(project, supportStatus));
			boolean isProjectActive = isProjectActive(project, trains);

			if (!isProjectActive || project == Projects.JDBC) {
				continue;
			}

			builder.append("| https://github.com/%s/%s[%s]".formatted(gitProject.getOwner(), gitProject.getRepositoryName(),
					project.getName())).append(System.lineSeparator());

			for (int i = 0; i < trains.size(); i++) {

				Train train = trains.get(i);
				TrainIteration iteration;

				if (!train.contains(project)) {

					if (project == Projects.JDBC) {
						project = Projects.RELATIONAL;
					} else if (project == Projects.RELATIONAL) {
						project = Projects.JDBC;
					}
				}

				if (!train.contains(project)) {
					builder.append("| ").append(System.lineSeparator());
					continue;
				}

				if (i == 0 && supportStatus == SupportStatus.OSS) {
					iteration = train.getIteration(Iteration.M1);
				} else {
					iteration = train.getIteration(Iteration.SR1);
				}

				Branch branch = Branch.from(iteration.getModule(project));

				builder.append(
						"| image:https://github.com/%1$s/%2$s/actions/workflows/ci.yml/badge.svg?branch=%3$s[\"CI\", link=\"https://github.com/%1$s/%2$s/actions/workflows/ci.yml?query=branch%%3A%3$s\"]"
								.formatted(gitProject.getOwner(), gitProject.getRepositoryName(), branch))
						.append(" +").append(System.lineSeparator());

				builder.append(
						"image:https://github.com/%1$s/%2$s/actions/workflows/snapshots.yml/badge.svg?branch=%3$s[\"Snapshots\", link=\"https://github.com/%1$s/%2$s/actions/workflows/snapshots.yml?query=branch%%3A%3$s\"]"
								.formatted(gitProject.getOwner(), gitProject.getRepositoryName(), branch))
						.append(System.lineSeparator());
			}

			builder.append(System.lineSeparator());
		}

		return builder.toString();
	}

	private static List<Train> getTrains(String trainNames) {

		if (StringUtils.hasText(trainNames)) {

			try {
				List<Train> trains = ReleaseTrains.latest(Integer.parseInt(trainNames));
				Collections.reverse(trains);
				return trains;
			} catch (NumberFormatException e) {
				return Stream.of(trainNames.split(",")).map(String::trim) //
						.map(ReleaseTrains::getTrainByName).collect(Collectors.toList());
			}
		} else {
			return ReleaseTrains.latest(3);
		}
	}

	private static boolean isProjectActive(Project project, List<Train> trains) {

		int containsProject = 0;
		for (Train train : trains) {
			if (train.contains(project)) {
				containsProject++;
			}
		}
		return containsProject > 0;
	}

	public void triggerAntoraWorkflow(SupportedProject project) {
		gitHub.triggerAntoraWorkflow(project);
	}

	private void createOrUpdateRelease(ModuleIteration module, TrainIteration previousIteration) {
		List<TicketReference> ticketReferences = git.getTicketReferencesBetween(module.getSupportedProject(),
				previousIteration, module.getTrainIteration());
		gitHub.createOrUpdateRelease(module, ticketReferences);
	}

}
