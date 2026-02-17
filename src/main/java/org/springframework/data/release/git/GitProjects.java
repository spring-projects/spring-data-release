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
package org.springframework.data.release.git;

import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportStatus;
import org.springframework.data.release.model.SupportedProject;

/**
 * Utility for Git projects.
 *
 * @author Mark Paluch
 */
public class GitProjects {

	/**
	 * Determine a supported project for the given repository name.
	 *
	 * @param repository
	 * @return
	 */
	public static SupportedProject getSupportedProject(String repository) {

		for (SupportStatus supportStatus : SupportStatus.values()) {

			for (Project project : Projects.all(supportStatus)) {
				GitProject gitProject = GitProject.of(project, supportStatus);

				if (gitProject.getRepositoryName().equals(repository)) {
					return gitProject.getProject();
				}
			}

		}

		throw new IllegalArgumentException("Cannot find project for repository " + repository);
	}
}
