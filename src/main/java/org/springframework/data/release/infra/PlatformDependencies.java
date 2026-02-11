/*
 * Copyright 2026-present the original author or authors.
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
package org.springframework.data.release.infra;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.release.issues.github.GitHubRepository;

/**
 * @author Mark Paluch
 */
class PlatformDependencies {

	static final Map<Dependency, List<GitHubRepository>> REPOSITORIES = new HashMap<>();

	static {
		REPOSITORIES.put(Dependencies.SPRING_FRAMEWORK, List.of(GitHubRepository.of("spring-projects", "spring-framework"),
				GitHubRepository.of("spring-projects", "spring-framework-commercial")));
		REPOSITORIES.put(Dependencies.SPRING_LDAP, List.of(GitHubRepository.of("spring-projects", "spring-ldap"),
				GitHubRepository.of("spring-projects", "spring-ldap-commercial")));
		REPOSITORIES.put(Dependencies.PROJECT_REACTOR,
				List.of(GitHubRepository.of("reactor", "reactor"), GitHubRepository.of("reactor", "reactor-commercial")));
		REPOSITORIES.put(Dependencies.MICROMETER, List.of(GitHubRepository.of("micrometer-metrics", "micrometer")));
	}

	public static Dependency findDependency(GitHubRepository repository) {
		return REPOSITORIES.entrySet().stream().filter(it -> it.getValue().equals(repository)).map(Map.Entry::getKey)
				.findFirst().orElse(null);
	}

	public static Collection<GitHubRepository> getRepositories() {
		return REPOSITORIES.values().stream().flatMap(Collection::stream).toList();
	}

}
