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
package org.springframework.data.release.git;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;

/**
 * @author Oliver Gierke
 */
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class GitProject {

	private static final String PROJECT_PREFIX = "spring-data";

	private final @Getter SupportedProject project;
	private final GitServer server;

	public static GitProject of(SupportedProject project) {
		return new GitProject(project, GitServer.INSTANCE);
	}

	public static GitProject of(ModuleIteration module) {
		return new GitProject(module.getSupportedProject(), GitServer.INSTANCE);
	}

	/**
	 * Returns the name of the repository the project is using.
	 *
	 * @return
	 */
	public String getRepositoryName() {

		String logicalName = "%s-%s".formatted(PROJECT_PREFIX,
				project.getProject() == Projects.JDBC ? "relational" : project.getName().toLowerCase());

		return project.isCommercial() ? logicalName + "-commercial" : logicalName;
	}

	/**
	 * Returns the URI of the {@link Project}'s repository.
	 *
	 * @return
	 */
	public String getProjectUri() {
		return server.getUri() + getRepositoryName();
	}
}
