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

import lombok.Value;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Tracker;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.util.Assert;

/**
 * Value object to represent a {@link Ticket}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Value
public class Ticket {

	String id, summary;
	String url;
	String assignee;
	TicketStatus ticketStatus;
	Set<TicketType> type;

	public Ticket(String id, String summary, String url, String assignee, TicketStatus ticketStatus) {
		this(id, summary, url, assignee, ticketStatus, Set.of());
	}

	public Ticket(String id, String summary, TicketStatus ticketStatus) {
		this(id, summary, ticketStatus, Set.of());
	}

	public Ticket(String id, String summary, String url, String assignee, TicketStatus ticketStatus,
			Set<TicketType> type) {
		this.id = id;
		this.summary = summary;
		this.url = url;
		this.assignee = assignee;
		this.type = type;
		this.ticketStatus = ticketStatus;
	}

	public Ticket(String id, String summary, TicketStatus ticketStatus, Set<TicketType> type) {
		this.id = id;
		this.summary = summary;
		this.type = type;
		this.assignee = null;
		this.url = null;
		this.ticketStatus = ticketStatus;
	}

	public static Ticket open(String id, String summary, TicketType ticketType) {
		return new Ticket(id, summary, TicketStatus.open(), EnumSet.of(ticketType));
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return String.format("%14s - %s%s (%s)", id, type.isEmpty() ? "" : type + " ", summary, url);
	}

	public boolean isResolved() {
		return ticketStatus.isResolved();
	}

	/**
	 * Returns whether the current {@link Ticket} is the release ticket for the given {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	public boolean isReleaseTicketFor(ModuleIteration module) {

		Assert.notNull(module, "Module must not be null!");
		return summary.startsWith(Tracker.releaseTicketSummary(module));
	}

	/**
	 * Returns whether the current {@link Ticket} is the release ticket by checking the summary prefix.
	 *
	 * @return
	 */
	public boolean isReleaseTicket() {
		return summary.startsWith(Tracker.RELEASE_PREFIX);
	}

	/**
	 * Returns whether the current {@link Ticket} is a release ticket for the given {@link TrainIteration}.
	 *
	 * @param train must not be {@literal null}.
	 * @return
	 */
	public boolean isReleaseTicketFor(TrainIteration train) {
		return train.stream().anyMatch(this::isReleaseTicketFor);
	}

	public boolean isAssignedTo(String username) {
		return username.equals(assignee);
	}
}
