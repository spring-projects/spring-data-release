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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.issues.github.GitHub;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.Module;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.util.Streamable;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@CliComponent
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IssueTrackerCommands extends TimedCommand {

	@NonNull GitHub gitHub;
	@NonNull ExecutorService executor;

	@CliCommand("tracker evict")
	public void evict() {
		gitHub.reset();
	}

	@CliCommand(value = "tracker tickets")
	public String tickets(@CliOption(key = "", mandatory = true) TrainIteration iteration, //
			@CliOption(key = "for-current-user", specifiedDefaultValue = "true",
					unspecifiedDefaultValue = "false") boolean forCurrentUser,
			@CliOption(key = "module") String moduleName,
			@CliOption(key = "release-tickets", specifiedDefaultValue = "true",
					unspecifiedDefaultValue = "false") boolean releaseTickets,
			@CliOption(key = "dependency-upgrades", specifiedDefaultValue = "true",
					unspecifiedDefaultValue = "false") boolean dependencyUpgrades,
			@CliOption(key = "tasks", specifiedDefaultValue = "true", unspecifiedDefaultValue = "false") boolean tasks,
			@CliOption(key = "resolved", specifiedDefaultValue = "true") Boolean resolved) {

		Predicate<Ticket> predicate = isReleaseTicket(releaseTickets);

		if (resolved != null) {
			predicate = predicate.and(it -> it.isResolved() == resolved);
		}

		predicate = predicate.and(it -> {
			return dependencyUpgrades || !it.getType().contains(TicketType.DependencyUpgrade);
		});

		predicate = predicate.and(it -> {
			return tasks || !it.getType().contains(TicketType.Task);
		});

		return getTickets(iteration, moduleName, forCurrentUser, predicate);
	}

	@CliCommand("tracker tickets list-open")
	public String openTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration, //
			@CliOption(key = "module") String moduleName,
			@CliOption(key = "filter-release-tickets") Boolean filterReleaseTickets) {
		return tickets(iteration, false, moduleName, !(filterReleaseTickets == null || filterReleaseTickets), true, true,
				false);
	}

	@CliCommand("tracker tickets list")
	public String allTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration, //
			@CliOption(key = "module") String moduleName,
			@CliOption(key = "filter-release-tickets") Boolean filterReleaseTickets) {
		return tickets(iteration, false, moduleName, !(filterReleaseTickets == null || filterReleaseTickets), true, true,
				null);
	}

	@CliCommand(value = "tracker tickets create")
	public String createTickets(@CliOption(key = "iteration", mandatory = true) TrainIteration iteration,
			@CliOption(key = "subject", mandatory = true) String subject,
			@CliOption(key = "description", mandatory = false) String description) {

		Predicate<ModuleIteration> isBuildProject = module -> module.getProject() == Projects.BUILD;

		List<Ticket> tickets = iteration.stream() //
				.filter(isBuildProject.negate()) //
				.map(module -> gitHub.createTicket(module, subject, TicketType.Task, false)).collect(Collectors.toList());

		StringBuilder body = new StringBuilder();

		for (Ticket ticket : tickets) {
			body.append("- [ ] ").append(ticket.getUrl()).append("\n");
		}

		ModuleIteration module = iteration.getModule(Projects.BUILD);
		Ticket buildTicket = gitHub.createTicket(module, subject, body.toString(), TicketType.Task, false);

		List<Ticket> allTickets = new ArrayList<>();
		allTickets.add(buildTicket);
		allTickets.addAll(tickets);

		return new Tickets(allTickets).toString();
	}

	@CliCommand(value = "tracker releasetickets list")
	public String releaseTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		return runAndReturn(executor, iteration, gitHub::getReleaseTicketFor,
				Tickets.toTicketsCollector()).toString();
	}

	/**
	 * Close the {@link TrainIteration} milestones and release tickets and create milestones and release tickets for
	 * {@code TrainIteration + 1}. This command is useful for service releases only.
	 *
	 * @param iteration
	 * @return
	 */
	@CliCommand(value = "tracker post-release")
	public String postRelease(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		Iteration i = iteration.getIteration();
		Assert.isTrue(i.isGAIteration() || i.isServiceIteration(), "Train iteration must be a GA or service iteration!");

		evict();
		Scheduler scheduler = Schedulers.boundedElastic();
		CompletableFuture<Void> close = Flux.fromIterable(withReleaseProject(iteration))
				.flatMap(it -> Mono.fromRunnable(() -> gitHub.closeIteration(it)).subscribeOn(scheduler)).then().toFuture();

		CompletableFuture<String> setupNext = Mono.fromCallable(() -> trackerSetupNext(iteration.nextIteration()))
				.subscribeOn(scheduler).toFuture();

		close.join();
		return setupNext.join();
	}

	/**
	 * Prepare this release by self-assigning release tickets and setting them to in-progress.
	 *
	 * @param iteration
	 * @return
	 */
	@CliCommand(value = "tracker prepare")
	public String trackerPrepare(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		return selfAssignReleaseTickets(iteration);
	}

	@CliCommand("tracker close")
	public void closeIteration(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		evict();
		run(executor, withReleaseProject(iteration), gitHub::closeIteration);
	}

	/**
	 * Prepare a new, upcoming release by creating release versions and release tickets.
	 *
	 * @param iteration
	 * @return
	 */
	@CliCommand(value = "tracker setup-next")
	public String trackerSetupNext(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		createReleaseVersions(iteration);

		for (int i = 0; i < 2; i++) {
			try {
				return createReleaseTickets(iteration);
			} catch (RuntimeException e) {
				try {
					Thread.sleep(1500); // give GitHub a bit of time to make tickets visible
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			}
		}

		return createReleaseTickets(iteration);
	}

	@CliCommand(value = "tracker releasetickets self-assign")
	public String selfAssignReleaseTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		return runAndReturn(executor, iteration, gitHub::assignReleaseTicketToMe,
				Tickets.toTicketsCollector()).toString();
	}

	@CliCommand(value = "tracker releaseversions create")
	public void createReleaseVersions(@CliOption(key = "", mandatory = true) TrainIteration iteration) {
		run(executor, withReleaseProject(iteration), gitHub::createReleaseVersion);
	}

	@CliCommand(value = "tracker releasetickets create")
	public String createReleaseTickets(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		run(executor, iteration, gitHub::createReleaseTicket);

		try {
			Thread.sleep(500); // give GitHub a bit of time to make tickets visible
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}

		return releaseTickets(iteration);
	}

	private static Predicate<Ticket> isReleaseTicket(boolean filterReleaseTickets) {
		return it -> filterReleaseTickets == it.isReleaseTicket();
	}

	private String getTickets(TrainIteration iteration, String moduleName, boolean forCurrentUser,
			Predicate<Ticket> ticketPredicate) {

		if (StringUtils.hasText(moduleName)) {
			return getTicketsForProject(iteration, Projects.requiredByName(moduleName), forCurrentUser, ticketPredicate);
		}

		return ExecutionUtils.runAndReturn(executor, iteration, moduleIteration -> {
			return getTicketsForProject(iteration, moduleIteration.getModule().getProject(), forCurrentUser, ticketPredicate);
		}).stream() //
				.filter(StringUtils::hasText) //
				.collect(Collectors.joining("\n"));
	}

	private String getTicketsForProject(TrainIteration iteration, Project project, boolean forCurrentUser,
			Predicate<Ticket> ticketPredicate) {

		ModuleIteration module = iteration.getModule(project);

		return gitHub.getTicketsFor(module, forCurrentUser) //
				.stream() //
				.filter(ticketPredicate) //
				.collect(Tickets.toTicketsCollector()) //
				.toString(false);
	}

	private static Streamable<ModuleIteration> withReleaseProject(TrainIteration iteration) {

		ModuleIteration bom = iteration.getModule(Projects.BOM);
		return iteration.and(new ModuleIteration(new Module(Projects.RELEASE, bom.getVersion()), iteration));
	}

}
