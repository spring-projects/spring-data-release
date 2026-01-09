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
package org.springframework.data.release.issues;

import static org.springframework.data.release.utils.ExecutionUtils.*;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.model.Module;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.util.Streamable;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.util.StringUtils;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@CliComponent
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IssueTrackerCommands extends TimedCommand {

	@NonNull PluginRegistry<IssueTracker, SupportedProject> tracker;
	@NonNull Executor executor;

	@CliCommand("tracker evict")
	public void evict() {
		StreamSupport.stream(tracker.spliterator(), false).forEach(IssueTracker::reset);
	}

	@CliCommand(value = "tracker tickets")
	public String jira(@CliOption(key = "", mandatory = true) TrainIteration iteration, //
			@CliOption(key = "for-current-user", specifiedDefaultValue = "true",
					unspecifiedDefaultValue = "false") boolean forCurrentUser) {

		return tracker.getPlugins().stream().//
				flatMap(it -> it.getTicketsFor(iteration, forCurrentUser).stream()).//
				collect(Tickets.toTicketsCollector()).toString();
	}

	@CliCommand(value = "tracker releasetickets")
	public String releaseTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		return runAndReturn(executor, iteration, module -> getTrackerFor(module).getReleaseTicketFor(module),
				Tickets.toTicketsCollector()).toString();
	}

	/**
	 * Prepare this release by self-assigning release tickets and setting them to in-progress.
	 *
	 * @param iteration
	 * @return
	 */
	@CliCommand(value = "tracker prepare")
	public String trackerPrepare(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		jiraSelfAssignReleaseTickets(iteration);

		return jiraStartProgress(iteration);
	}

	/**
	 * Prepare a new, upcoming release by creating release versions and release tickets.
	 *
	 * @param iteration
	 * @return
	 */
	@CliCommand(value = "tracker setup-next")
	public String trackerSetupNext(@CliOption(key = "", mandatory = true) TrainIteration iteration)
			throws InterruptedException {

		createReleaseVersions(iteration);

		Thread.sleep(500); // give GitHub a bit of time to make tickets visible

		return createReleaseTickets(iteration);
	}

	@CliCommand(value = "tracker self-assign releasetickets")
	public String jiraSelfAssignReleaseTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		return runAndReturn(executor, iteration, module -> getTrackerFor(module).assignReleaseTicketToMe(module),
				Tickets.toTicketsCollector()).toString();
	}

	public String jiraStartProgress(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		return runAndReturn(executor, iteration, module -> getTrackerFor(module).startReleaseTicketProgress(module),
				Tickets.toTicketsCollector()).toString();
	}

	@CliCommand(value = "tracker create releaseversions")
	public void createReleaseVersions(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		run(executor, withReleaseProject(iteration), module -> getTrackerFor(module).createReleaseVersion(module));
	}

	@CliCommand(value = "tracker create releasetickets")
	public String createReleaseTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		run(executor, iteration, module -> getTrackerFor(module).createReleaseTicket(module));
		evict();

		return releaseTickets(iteration);
	}

	@CliCommand(value = "tracker create tickets")
	public String createTickets(@CliOption(key = "iteration", mandatory = true) TrainIteration iteration,
			@CliOption(key = "subject", mandatory = true) String subject,
			@CliOption(key = "description", mandatory = false) String description) {

		Predicate<ModuleIteration> isBuildProject = module -> module.getProject() == Projects.BUILD;

		List<Ticket> tickets = iteration.stream() //
				.filter(isBuildProject.negate()) //
				.map(module -> getTrackerFor(module).createTicket(module, subject, IssueTracker.TicketType.Task, false))
				.collect(Collectors.toList());

		StringBuilder body = new StringBuilder();

		for (Ticket ticket : tickets) {
			body.append("- [ ] ").append(ticket.getUrl()).append("\n");
		}

		ModuleIteration module = iteration.getModule(Projects.BUILD);
		Ticket buildTicket = getTrackerFor(module).createTicket(module, subject, body.toString(),
				IssueTracker.TicketType.Task, false);

		List<Ticket> allTickets = new ArrayList<>();
		allTickets.add(buildTicket);
		allTickets.addAll(tickets);

		return new Tickets(allTickets).toString();
	}

	@CliCommand("tracker open-tickets")
	public String openTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration, //
			@CliOption(key = "module") String moduleName,
			@CliOption(key = "filter-release-tickets") Boolean filterReleaseTickets) {

		Predicate<Ticket> notResolved = it -> !it.isResolved();

		return getTickets(iteration, moduleName,
				notResolved.and(getFilterPredicate(filterReleaseTickets == null || filterReleaseTickets)));
	}

	@CliCommand("tracker all-tickets")
	public String allTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration, //
			@CliOption(key = "module") String moduleName,
			@CliOption(key = "filter-release-tickets") Boolean filterReleaseTickets) {

		return getTickets(iteration, moduleName, getFilterPredicate(filterReleaseTickets == null || filterReleaseTickets));
	}

	private static Predicate<Ticket> getFilterPredicate(boolean filterReleaseTickets) {
		return it -> filterReleaseTickets == !it.isReleaseTicket();
	}

	private String getTickets(TrainIteration iteration, String moduleName, Predicate<Ticket> ticketPredicate) {

		if (StringUtils.hasText(moduleName)) {
			return getTicketsForProject(iteration, Projects.requiredByName(moduleName), ticketPredicate);
		}

		return ExecutionUtils.runAndReturn(executor, iteration, moduleIteration -> {
			return getTicketsForProject(iteration, moduleIteration.getModule().getProject(), ticketPredicate);
		}).stream() //
				.filter(StringUtils::hasText) //
				.collect(Collectors.joining("\n"));
	}

	private String getTicketsForProject(TrainIteration iteration, Project project, Predicate<Ticket> ticketPredicate) {

		ModuleIteration module = iteration.getModule(project);

		return getTrackerFor(module).getTicketsFor(module) //
				.stream() //
				.filter(ticketPredicate) //
				.collect(Tickets.toTicketsCollector()) //
				.toString(false);
	}

	@CliCommand("tracker close")
	public void closeIteration(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		run(executor, withReleaseProject(iteration), module -> getTrackerFor(module).closeIteration(module));
	}

	@CliCommand("tracker archive")
	public void archiveIteration(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		run(executor, iteration, module -> getTrackerFor(module).archiveReleaseVersion(module));
	}

	private static Streamable<ModuleIteration> withReleaseProject(TrainIteration iteration) {

		ModuleIteration bom = iteration.getModule(Projects.BOM);
		return iteration.and(new ModuleIteration(new Module(Projects.RELEASE, bom.getVersion()), iteration));
	}

	public IssueTracker getTrackerFor(ModuleIteration moduleIteration) {
		return getTrackerFor(moduleIteration.getSupportedProject());
	}

	public IssueTracker getTrackerFor(SupportedProject project) {
		return tracker.getRequiredPluginFor(project, () -> String.format("No issue tracker found for module %s!", project));
	}
}
