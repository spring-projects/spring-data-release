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
package org.springframework.data.release.issues.github;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.data.release.git.Branch;
import org.springframework.data.release.git.GitProject;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportStatus;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;

/**
 * Utility to generate the GitHub build status matrix for the wiki.
 *
 * @author Mark Paluch
 */
class BuildStatusMatrix {

	public static String createBuildStatusMatrix(List<Train> trains) {

		List<Project> projects = new ArrayList<>(15);
		projects.add(Projects.BOM);
		projects.addAll(Projects.all());
		projects.sort(Comparator.comparing(it -> it.getName().toLowerCase(Locale.ROOT)));

		SupportStatus supportStatus = SupportStatus.OSS;

		for (Train train : trains) {
			if (train.isCommercial()) {
				supportStatus = SupportStatus.COMMERCIAL;
				break;
			}
		}

		StringBuilder builder = new StringBuilder();

		for (Project project : projects) {

			GitProject gitProject = GitProject.of(SupportedProject.of(project, supportStatus));
			boolean isProjectActive = isProjectActive(project, trains);

			if (!isProjectActive || project == Projects.JDBC) {
				continue;
			}

			builder.append("| https://github.com/%s/%s[%s]".formatted(gitProject.getOwner(), gitProject.getRepositoryName(),
					project.getName())).append(System.lineSeparator());

			for (int i = 0; i < trains.size(); i++) {

				Train train = trains.get(i);
				TrainIteration iteration;

				if (!train.contains(project)) {

					if (project == Projects.JDBC) {
						project = Projects.RELATIONAL;
					} else if (project == Projects.RELATIONAL) {
						project = Projects.JDBC;
					}
				}

				if (!train.contains(project)) {
					builder.append("| ").append(System.lineSeparator());
					continue;
				}

				if (i == 0 && supportStatus == SupportStatus.OSS) {
					iteration = train.getIteration(Iteration.M1);
				} else {
					iteration = train.getIteration(Iteration.SR1);
				}

				Branch branch = Branch.from(iteration.getModule(project));

				builder.append(
						"| image:https://github.com/%1$s/%2$s/actions/workflows/ci.yml/badge.svg?branch=%3$s[\"CI\", link=\"https://github.com/%1$s/%2$s/actions/workflows/ci.yml?query=branch%%3A%3$s\"]"
								.formatted(gitProject.getOwner(), gitProject.getRepositoryName(), branch))
						.append(" +").append(System.lineSeparator());

				builder.append(
						"image:https://github.com/%1$s/%2$s/actions/workflows/snapshots.yml/badge.svg?branch=%3$s[\"Snapshots\", link=\"https://github.com/%1$s/%2$s/actions/workflows/snapshots.yml?query=branch%%3A%3$s\"]"
								.formatted(gitProject.getOwner(), gitProject.getRepositoryName(), branch))
						.append(System.lineSeparator());
			}

			builder.append(System.lineSeparator());
		}

		return builder.toString();
	}

	private static boolean isProjectActive(Project project, List<Train> trains) {

		int containsProject = 0;
		for (Train train : trains) {
			if (train.contains(project)) {
				containsProject++;
			}
		}
		return containsProject > 0;
	}

}
