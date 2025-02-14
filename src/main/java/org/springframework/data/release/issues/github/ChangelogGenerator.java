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

package org.springframework.data.release.issues.github;

import lombok.Getter;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates a changelog markdown file which includes bug fixes, enhancements and contributors for a given milestone.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 */
public class ChangelogGenerator {

	private static final Pattern ghUserMentionPattern = Pattern.compile("(^|[^\\w`])(@[\\w-]+)");

	@Getter private final Set<String> excludeLabels;

	@Getter private final Set<String> excludeContributors;

	@Getter private final String contributorsTitle;

	private final ChangelogSections sections;

	public ChangelogGenerator() {
		this.excludeLabels = new HashSet<>(Collections.singletonList("type: task"));
		this.excludeContributors = new LinkedHashSet<>();
		this.contributorsTitle = null;
		this.sections = new ChangelogSections();
	}

	/**
	 * Generates a file at the given path which includes bug fixes, enhancements and contributors for the given milestone.
	 *
	 * @param issues the issues to generate the changelog for
	 * @param sectionContentPostProcessor the postprocessor for a changelog section
	 * @param includeIssueNumbers whether to include issue numbers
	 */
	public String generate(List<ChangeItem> issues,
			BiFunction<ChangelogSection, String, String> sectionContentPostProcessor, boolean includeIssueNumbers) {
		return generateContent(issues, sectionContentPostProcessor, includeIssueNumbers);
	}

	private boolean isExcluded(GitHubReadIssue issue) {
		return issue.getLabels().stream().anyMatch(this::isExcluded);
	}

	private boolean isExcluded(Label label) {
		return this.excludeLabels.contains(label.getName());
	}

	private String generateContent(List<ChangeItem> issues,
			BiFunction<ChangelogSection, String, String> sectionContentPostProcessor, boolean includeIssueNumbers) {
		StringBuilder content = new StringBuilder();
		addSectionContent(content,
				this.sections.collate(issues.stream().filter(it -> it.getReference().isIssue() || it.getReference().isRelated())
						.map(ChangeItem::getIssue).collect(Collectors.toList())),
				sectionContentPostProcessor, includeIssueNumbers);
		Set<GitHubUser> contributors = getContributors(issues);
		if (!contributors.isEmpty()) {
			addContributorsContent(content, contributors);
		}
		return content.toString();
	}

	private void addSectionContent(StringBuilder result, Map<ChangelogSection, List<GitHubReadIssue>> sectionIssues,
			BiFunction<ChangelogSection, String, String> sectionContentPostProcessor, boolean includeIssueNumbers) {

		sectionIssues.forEach((section, issues) -> {

			issues.sort(Comparator.reverseOrder());

			StringBuilder content = new StringBuilder();

			content.append("## ").append(section).append(String.format("%n"));
			issues.stream().map(issue -> getFormattedIssue(issue, includeIssueNumbers)).forEach(content::append);

			result.append((result.length() != 0) ? String.format("%n") : "");
			result.append(sectionContentPostProcessor.apply(section, content.toString()));
		});
	}

	private String getFormattedIssue(GitHubReadIssue issue, boolean includeIssueNumbers) {
		String title = issue.getTitle();
		title = ghUserMentionPattern.matcher(title).replaceAll("$1`$2`");
		return includeIssueNumbers ? String.format("- %s %s%n", title, getLinkToIssue(issue))
				: String.format("- %s%n", title);
	}

	private String getLinkToIssue(GitHubIssue issue) {
		return "[" + issue.getId() + "]" + "(" + issue.getUrl() + ")";
	}

	private Set<GitHubUser> getContributors(List<ChangeItem> issues) {
		if (this.excludeContributors.contains("*")) {
			return Collections.emptySet();
		}
		return issues.stream()
				.filter(item -> item.getReference().isPullRequest() || item.getIssue().getPullRequest() != null) //
				.map(ChangeItem::getIssue) //
				.map(GitHubReadIssue::getUser) //
				.filter(this::isIncludedContributor) //
				.collect(Collectors.toSet());
	}

	private boolean isIncludedContributor(GitHubUser user) {
		return !this.excludeContributors.contains(user.getName());
	}

	private void addContributorsContent(StringBuilder content, Set<GitHubUser> contributors) {
		content.append(String.format("%n## "));
		content.append((this.contributorsTitle != null) ? this.contributorsTitle : ":heart: Contributors");
		content.append(String.format("%nWe'd like to thank all the contributors who worked on this release!%n%n"));
		contributors.stream().map(this::formatContributors).forEach(content::append);
	}

	private String formatContributors(GitHubUser c) {
		return String.format("- @%s%n", c.getName());
	}

}
