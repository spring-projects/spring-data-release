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

import java.util.Comparator;

import org.springframework.data.release.issues.github.GitHubRepository;

/**
 * @author Mark Paluch
 */
@Value
public class TicketReference implements Comparable<TicketReference> {

	String id;
	String message;
	Style style;
	Reference reference;
	GitHubRepository repository;

	public TicketReference(String id, String message, Style style, Reference reference, GitHubRepository repository) {
		this.id = normalize(id);
		this.message = message;
		this.style = style;
		this.reference = reference;
		this.repository = repository;
	}

	public static TicketReference ofTicket(String number, Style style, GitHubRepository repository) {
		return new TicketReference(number, "", style, Reference.Ticket, repository);
	}

	private static String normalize(String id) {

		if (id.toLowerCase().startsWith("gh-")) {
			return "#" + id.substring(3);
		}

		return id.toUpperCase().replaceAll(" ", "");
	}

	@Override
	public int compareTo(TicketReference o) {

		Comparator<TicketReference> reference = Comparator.comparing(TicketReference::getRepository);

		if (id.startsWith("#") && o.id.startsWith("#")) {

			reference = reference.thenComparing(it -> Integer.parseInt(it.getId().substring(1)), Integer::compareTo);
		} else {
			reference = reference.thenComparing((o1, o2) -> o1.id.compareToIgnoreCase(o2.id));
		}

		return reference.compare(this, o);
	}

	public boolean isIssue() {
		return getReference() == Reference.Ticket;
	}

	public boolean isRelated() {
		return getReference() == Reference.Related;
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
