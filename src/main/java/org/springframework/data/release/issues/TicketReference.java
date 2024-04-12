/*
 * Copyright 2020-2022 the original author or authors.
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

/**
 * @author Mark Paluch
 */
@Value
public class TicketReference implements Comparable<TicketReference> {

	String id;
	String message;
	Style style;
	Reference reference;

	public TicketReference(String id, String message, Style style, Reference reference) {
		this.id = normalize(id);
		this.message = message;
		this.style = style;
		this.reference = reference;
	}

	public static TicketReference ofTicket(String number, Style style) {
		return new TicketReference(number, "", style, Reference.Ticket);
	}

	private static String normalize(String id) {

		if (id.toLowerCase().startsWith("gh-")) {
			return "#" + id.substring(3);
		}

		return id.toUpperCase().replaceAll(" ", "");
	}

	@Override
	public int compareTo(TicketReference o) {

		if (id.startsWith("#") && o.id.startsWith("#")) {
			return Integer.compare(Integer.parseInt(id.substring(1)), Integer.parseInt(o.id.substring(1)));
		}

		return id.compareToIgnoreCase(o.id);
	}

	public boolean isIssue() {
		return getReference() == Reference.Ticket;
	}

	public boolean isPullRequest() {
		return getReference() == Reference.PullRequest;
	}

	public enum Style {
		GitHub, Jira
	}

	public enum Reference {
		Ticket, Related, PullRequest
	}
}
