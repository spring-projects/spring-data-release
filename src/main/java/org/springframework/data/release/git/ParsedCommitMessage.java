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
package org.springframework.data.release.git;

import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.release.issues.TicketReference;
import org.springframework.lang.Nullable;

/**
 * Value object representing a parsed commit message. The {@link #parse(String)} method inspects a commit message to
 * extract a {@link TicketReference}, related tickets, a pull request reference and summary/body from the commit. Commit
 * messages may used {@code &lt;ticket&gt; - summary} syntax for Jira and GitHub tickets (gh- and # notation). This
 * parser also supports {@code Original pull request}, {@code Related ticket} and GitHub close keywords.
 *
 * @author Mark Paluch
 */
@Getter
@ToString
class ParsedCommitMessage {

	private static final Pattern JIRA_TICKET = Pattern.compile("(?>\\[)?([A-Z]+[ ]?-[ ]?\\d+)(?>\\])?");
	private static final Pattern GITHUB_TICKET = Pattern.compile("((?>#|gh-)\\d+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern GITHUB_CLOSE_SYNTAX = Pattern.compile(
			"(?>closes|closed|close|fixes|fixed|fix|resolves|resolved|resolve)[\\s:]*((?>#|gh-)\\d+)",
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private static final Pattern GITHUB_SEE_SYNTAX = Pattern.compile("(?>see|related to)[\\s:]*((?>#|gh-)\\d+)",
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private static final Pattern GITHUB_PREFIX_SYNTAX = Pattern.compile("^((?>#|gh-)\\d+)");

	private static final Pattern A_TICKET = Pattern
			.compile("(%s|%s)".formatted(JIRA_TICKET.pattern(), GITHUB_TICKET.pattern()), Pattern.CASE_INSENSITIVE);

	private static final Pattern ORIGINAL_PULL_REQUEST = Pattern
			.compile("Original (?>pull request|PR|pullrequest)[:]*(?>\\s+)?" + A_TICKET.pattern(), Pattern.CASE_INSENSITIVE);

	private static final Pattern RELATED_TICKET = Pattern
			.compile("(?>(?>Related (?>tickets|ticket))|(?>Ticket)|(?>Related))[:]*(?>\\s+)?((" + A_TICKET.pattern()
					+ "(?>[\\s,]*))+)", Pattern.CASE_INSENSITIVE);

	private final String summary;
	private final @Nullable String body;

	private final TicketReference ticketReference;
	private final TicketReference pullRequestReference;
	private final List<TicketReference> relatedTickets;

	private ParsedCommitMessage(String summary, @Nullable String body) {

		this.summary = summary;
		this.body = body;

		TicketReference ticketReference = null;
		TicketReference pullRequestReference = null;

		// DATACASS-nnn - syntax
		Optional<TicketReference> jiraTicket = tryParseJiraTicketReference(summary);

		if (jiraTicket.isPresent()) {
			ticketReference = jiraTicket.get();
		}

		// Closes (gh-nnn|#nnn) syntax
		Matcher gitHubCloseMatcher = GITHUB_CLOSE_SYNTAX.matcher(summary + "\n" + body);
		Matcher gitHubSeeMatcher = GITHUB_SEE_SYNTAX.matcher(summary + "\n" + body);

		// #nnn syntax
		Optional<TicketReference> gitHubTicket = tryParseGitHubTicketReference(summary);

		if (gitHubTicket.isPresent()) {
			ticketReference = gitHubTicket.get();
		} else {
			if (gitHubCloseMatcher.find()) {
				ticketReference = new TicketReference(gitHubCloseMatcher.group(1), summary, TicketReference.Style.GitHub,
						TicketReference.Reference.Ticket);
			} else if (gitHubSeeMatcher.find()) {
				ticketReference = new TicketReference(gitHubSeeMatcher.group(1), summary, TicketReference.Style.GitHub,
						TicketReference.Reference.Ticket);
			}
		}

		List<TicketReference> relatedTickets = parseRelatedTickets(summary, body,
				Arrays.asList(gitHubCloseMatcher, gitHubSeeMatcher));
		Optional<TicketReference> optionalOriginalPr = parsePullRequestReference(summary, body);

		if (optionalOriginalPr.isPresent()) {

			pullRequestReference = optionalOriginalPr.get();

			if (ticketReference == null) {
				ticketReference = pullRequestReference;
				pullRequestReference = null;
			}
		}

		if (ticketReference == null && !relatedTickets.isEmpty()) {
			ticketReference = relatedTickets.get(0);
		}

		this.ticketReference = ticketReference;
		this.pullRequestReference = pullRequestReference;
		this.relatedTickets = relatedTickets;
	}

	/**
	 * Parse a commit message into {@link ParsedCommitMessage}.
	 *
	 * @param message
	 * @return
	 */
	public static ParsedCommitMessage parse(String message) {

		int lineBreak = message.indexOf('\n');

		String summary;
		String body;

		if (lineBreak > -1) {
			summary = message.substring(0, lineBreak).trim();
			body = message.substring(lineBreak + 1).trim();
		} else {
			summary = message.trim();
			body = null;
		}

		return new ParsedCommitMessage(summary, body);
	}

	protected static Optional<TicketReference> tryParseGitHubTicketReference(String summary) {

		Matcher gitHubPrefixMatcher = GITHUB_PREFIX_SYNTAX.matcher(summary);

		if (gitHubPrefixMatcher.find()) {

			MatchResult mr = gitHubPrefixMatcher.toMatchResult();
			if (mr.start(1) == 0) {

				int summaryStart = findSummaryIndex(summary, mr.end(1));

				return Optional.of(new TicketReference(gitHubPrefixMatcher.group(1).toUpperCase(Locale.ROOT),
						summaryStart > -1 ? summary.substring(summaryStart) : summary, TicketReference.Style.GitHub,
						TicketReference.Reference.Ticket));
			}
		}

		return Optional.empty();
	}

	protected static Optional<TicketReference> tryParseJiraTicketReference(String summary) {

		Matcher jiraMatcher = JIRA_TICKET.matcher(summary);

		if (jiraMatcher.find()) {

			MatchResult mr = jiraMatcher.toMatchResult();

			// allow […] syntax and start of message syntax
			if (mr.start(1) < 2) {
				int summaryStart = findSummaryIndex(summary, mr.end(1));

				return Optional.of(new TicketReference(jiraMatcher.group(1).toUpperCase(Locale.ROOT),
						summaryStart > -1 ? summary.substring(summaryStart) : summary, TicketReference.Style.Jira,
						TicketReference.Reference.Ticket));
			}
		}

		return Optional.empty();
	}

	protected static Optional<TicketReference> parsePullRequestReference(String summary, String body) {

		if (body != null) {

			Matcher prMatcher = ORIGINAL_PULL_REQUEST.matcher(body);

			if (prMatcher.find()) {
				return extractTicket(prMatcher.group(1), summary, TicketReference.Reference.PullRequest);
			}
		}

		return Optional.empty();
	}

	protected static List<TicketReference> parseRelatedTickets(String summary, String body,
			Collection<Matcher> gitHubMatcher) {

		List<TicketReference> relatedTickets = new ArrayList<>();
		if (body != null) {
			Matcher relatedTicketsMatcher = RELATED_TICKET.matcher(body);

			if (relatedTicketsMatcher.find()) {

				String[] ticketIds = relatedTicketsMatcher.group(1).split(",");

				for (String ticketId : ticketIds) {
					extractTicket(ticketId.trim(), summary, TicketReference.Reference.Related).ifPresent(relatedTickets::add);
				}
			}

			for (Matcher matcher : gitHubMatcher) {

				while (matcher.find()) {
					extractTicket(matcher.group(1), summary, TicketReference.Reference.Related).ifPresent(relatedTickets::add);
				}
			}

		}

		return relatedTickets;
	}

	protected static Optional<TicketReference> extractTicket(String ticketId, String summary,
			TicketReference.Reference reference) {

		if (GITHUB_TICKET.matcher(ticketId.trim()).matches()) {
			return Optional.of(new TicketReference(ticketId, summary, TicketReference.Style.GitHub, reference));
		}

		if (JIRA_TICKET.matcher(ticketId.trim()).matches()) {
			return Optional.of(new TicketReference(ticketId, summary, TicketReference.Style.Jira, reference));
		}

		return Optional.empty();
	}

	private static int findSummaryIndex(String summary, int startAt) {

		int dash = summary.indexOf("- ", startAt);

		if (dash > -1) {
			return dash + 2;
		}

		int space = summary.indexOf(" ", startAt);

		if (space > -1) {
			return space + 1;
		}

		return -1;
	}

	public List<TicketReference> getTicketReferences() {

		List<TicketReference> references = new ArrayList<>();
		if (getTicketReference() != null) {
			references.add(getTicketReference());
		}

		references.addAll(getRelatedTickets());

		if (getPullRequestReference() != null) {
			references.add(getPullRequestReference());
		}

		return references;
	}
}
