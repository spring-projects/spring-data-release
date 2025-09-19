/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.release.issues.github;

import lombok.Value;

import java.util.Comparator;

import org.springframework.util.StringUtils;

/**
 * Reference to a GitHub repository.
 *
 * @author Mark Paluch
 */
@Value(staticConstructor = "of")
public class GitHubRepository implements Comparable<GitHubRepository> {

	private static final GitHubRepository IMPLICIT = new GitHubRepository("", "");

	String namespace;
	String project;

	public static GitHubRepository implicit() {
		return IMPLICIT;
	}

	@Override
	public int compareTo(GitHubRepository o) {

		Comparator<GitHubRepository> repository = Comparator.comparing(GitHubRepository::isImplicit)
				.thenComparing(GitHubRepository::getNamespace).thenComparing(GitHubRepository::getProject);
		return repository.compare(this, o);
	}

	/**
	 * @return {@literal true} if the repository is explicitly defined.
	 */
	public boolean isDefined() {
		return StringUtils.hasText(namespace) && StringUtils.hasText(project);
	}

	/**
	 * @return {@literal true} if the repository is implicit (i.e. tickets are not qualified with a repository).
	 */
	public boolean isImplicit() {
		return !isDefined();
	}

	@Override
	public String toString() {
		return namespace + "/" + project;
	}
}
