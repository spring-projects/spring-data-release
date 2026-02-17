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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.git.GitProjects;
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

		List<Train> trains = new ArrayList<>(ReleaseTrains.getTrains(trainNames, 3));
		Collections.reverse(trains);
		return BuildStatusMatrix.createBuildStatusMatrix(trains);
	}

	@CliCommand(value = "github trigger-downstream-workflow")
	public void triggerDownstreamWorkflow(@CliOption(key = "source-repository") String sourceRepository,
			@CliOption(key = "workflow") String workflowName, @CliOption(key = "head-branch") String headBranch) {

		SupportedProject sourceProject = GitProjects.getSupportedProject(sourceRepository);
		TrainIteration iteration = gitHub.resolveTrainIteration(sourceProject, headBranch);

		ExecutionUtils.run(executor, iteration.filter(it -> it.getProject().dependsOn(sourceProject.getProject())), it -> {
			GitHubWorkflows.GitHubWorkflow workflow = gitHub.getWorkflow(it.getSupportedProject(), workflowName);
			gitHub.triggerDownstreamWorkflow(workflow, it);
		});
	}

	public void triggerAntoraWorkflow(SupportedProject project) {

		SupportedProject workflowRepository = SupportedProject.of(Projects.RELEASE, SupportStatus.COMMERCIAL);
		GitHubWorkflows.GitHubWorkflow antoraWorkflow = gitHub.getAntoraWorkflow(workflowRepository,
				project.getSupportStatus());
		gitHub.triggerAntoraWorkflow(antoraWorkflow, workflowRepository, project);
	}

	private void createOrUpdateRelease(ModuleIteration module, TrainIteration previousIteration) {
		List<TicketReference> ticketReferences = git.getTicketReferencesBetween(module.getSupportedProject(),
				previousIteration, module.getTrainIteration());
		gitHub.createOrUpdateRelease(module, ticketReferences);
	}

}
