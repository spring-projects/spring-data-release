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

import java.util.Collection;
import java.util.List;

import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.plugin.core.Plugin;

/**
 * Interface for issue tracker operations.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public interface IssueTracker extends Plugin<SupportedProject> {

	/**
	 * Reset internal state (cache, etc).
	 */
	void reset();

	/**
	 * Returns all {@link Tickets} for the given {@link TrainIteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @return
	 */
	Tickets getTicketsFor(TrainIteration iteration);

	/**
	 * Returns all {@link Tickets} for the given {@link ModuleIteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @return
	 */
	Tickets getTicketsFor(ModuleIteration iteration);

	/**
	 * Returns all {@link Tickets} for the given {@link ModuleIteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param forCurrentUser
	 * @return
	 */
	Tickets getTicketsFor(ModuleIteration iteration, boolean forCurrentUser);

	/**
	 * Returns all {@link Tickets} for the given {@link Train} and {@link Iteration}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param forCurrentUser
	 * @return
	 */
	Tickets getTicketsFor(TrainIteration iteration, boolean forCurrentUser);


	/**
	 * Returns the {@link Ticket} that tracks modifications in the context of a release.
	 *
	 * @param module the module to lookup the {@link Ticket} for, must not be {@literal null}.
	 * @return
	 */
	Ticket getReleaseTicketFor(ModuleIteration module);

	/**
	 * Query the issue tracker for multiple {@link Ticket#id ticket Ids}. Tickets that are not found are not returned
	 * within the result.
	 *
	 * @param project must not be {@literal null}.
	 * @param ticketIds collection of {@link Ticket#id ticket Ids}, must not be {@literal null}.
	 * @return
	 */
	Collection<Ticket> findTickets(SupportedProject project, Collection<TicketReference> ticketIds);

	/**
	 * Query the issue tracker for multiple {@link Ticket#id ticket Ids}. Tickets that are not found are not returned. The
	 * implementation ensures to resolve only references that match the issue tracker scheme this issue tracker is
	 * responsible for.
	 *
	 * @param moduleIteration must not be {@literal null}.
	 * @param ticketReferences must not be {@literal null}.
	 * @return
	 */
	Tickets findTickets(ModuleIteration moduleIteration, Collection<TicketReference> ticketIds);

	/**
	 * Creates a release version if release version is missing.
	 *
	 * @param module must not be {@literal null}.
	 */
	void createReleaseVersion(ModuleIteration module);

	/**
	 * Retire the release version from the active versions for a {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 */
	void archiveReleaseVersion(ModuleIteration module);

	/**
	 * Create release ticket if release ticket is missing.
	 * <p>
	 * TODO: Return created ticket
	 *
	 * @param module must not be {@literal null}.
	 */
	void createReleaseTicket(ModuleIteration module);

	/**
	 * Creates a ticket for the given {@link ModuleIteration} and summary {@code subject}.
	 *
	 * @param module must not be {@literal null}.
	 * @param subject the subject to use.
	 * @param ticketType the ticket type.
	 * @param assignToCurrentUser
	 * @return the created ticket.
	 */
	default Ticket createTicket(ModuleIteration module, String subject, TicketType ticketType,
			boolean assignToCurrentUser) {
		return createTicket(module, subject, "", ticketType, assignToCurrentUser);
	}

	/**
	 * Creates a ticket for the given {@link ModuleIteration} and summary {@code subject}.
	 *
	 * @param module must not be {@literal null}.
	 * @param subject the subject to use.
	 * @param description the description to use.
	 * @param ticketType the ticket type.
	 * @param assignToCurrentUser
	 * @return the created ticket.
	 */
	Ticket createTicket(ModuleIteration module, String subject, String description, TicketType ticketType,
			boolean assignToCurrentUser);

	/**
	 * Assigns the ticket to the current user.
	 *
	 * @param project must not be {@literal null}.
	 * @param ticket must not be {@literal null}.
	 */
	Ticket assignTicketToMe(SupportedProject project, Ticket ticket);

	/**
	 * Assigns the release ticket for the given {@link ModuleIteration} to the current user.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	Ticket assignReleaseTicketToMe(ModuleIteration module);

	/**
	 * Start progress on release tickets.
	 *
	 * @param module
	 * @return
	 */
	Ticket startReleaseTicketProgress(ModuleIteration module);

	/**
	 * Returns the {@link Changelog} for the given {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	Changelog getChangelogFor(ModuleIteration module);

	/**
	 * Returns the {@link Changelog} for the given {@link ModuleIteration} using {@link TicketReference}s.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	default Changelog getChangelogFor(ModuleIteration module, List<TicketReference> ticketReferences) {

		Tickets tickets = findTickets(module, ticketReferences);
		return Changelog.of(module, tickets);
	}

	/**
	 * Closes the given {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 */
	void closeIteration(ModuleIteration module);

	/**
	 * Resolve a {@link Ticket}.
	 *
	 * @param module must not be {@literal null}.
	 * @param ticket must not be {@literal null}.
	 */
	void closeTicket(ModuleIteration module, Ticket ticket);

}
