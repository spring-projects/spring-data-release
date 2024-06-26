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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * A single section of a changelog report.
 *
 * @author Phillip Webb
 */
public class ChangelogSection {

	private final String title;

	private final String group;

	private final Set<String> labels;

	ChangelogSection(String title, String group, String... labels) {
		this(title, group, new LinkedHashSet<>(Arrays.asList(labels)));
	}

	ChangelogSection(String title, String group, Set<String> labels) {
		Assert.hasText(title, "Title must not be empty");
		Assert.isTrue(!CollectionUtils.isEmpty(labels), "Labels must not be empty");
		this.title = title;
		this.group = group;
		this.labels = labels;
	}

	String getGroup() {
		return this.group;
	}

	boolean isMatchFor(GitHubReadIssue issue) {
		for (String candidate : this.labels) {
			for (Label label : issue.getLabels()) {
				if (label.getName().contains(candidate)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasLabel(String label) {
		return this.labels.contains(label);
	}

	@Override
	public String toString() {
		return this.title;
	}

}
