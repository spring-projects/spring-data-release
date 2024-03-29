/*
 * Copyright 2016-2022 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.TrainIteration;

/**
 * Unit tests for {@link GithubMilestone}.
 *
 * @author Mark Paluch
 */
class GithubMilestoneUnitTests {

	@Test
	void rendersGithubGaVersionCorrectly() {

		assertIterationVersion(Iteration.M1, "1.8 M1 (Dijkstra)");
		assertIterationVersion(Iteration.RC1, "1.8 RC1 (Dijkstra)");
		assertIterationVersion(Iteration.GA, "1.8 GA (Dijkstra)");

		assertIterationVersion(Iteration.SR1, "1.8.1 (Dijkstra SR1)");
		assertIterationVersion(Iteration.SR2, "1.8.2 (Dijkstra SR2)");
		assertIterationVersion(Iteration.SR3, "1.8.3 (Dijkstra SR3)");
		assertIterationVersion(Iteration.SR4, "1.8.4 (Dijkstra SR4)");
	}

	@Test
	void rendersGithubCalverCorrectly() {

		ModuleIteration m1 = ReleaseTrains.OCKHAM.getModuleIteration(Projects.COMMONS, Iteration.M1);
		ModuleIteration rc1 = ReleaseTrains.OCKHAM.getModuleIteration(Projects.COMMONS, Iteration.RC1);
		ModuleIteration ga = ReleaseTrains.OCKHAM.getModuleIteration(Projects.COMMONS, Iteration.GA);

		assertThat(new GithubMilestone(m1).getDescription()).isEqualTo("2020.0.0-M1");
		assertThat(new GithubMilestone(rc1).getDescription()).isEqualTo("2020.0.0-RC1");
		assertThat(new GithubMilestone(ga).getDescription()).isEqualTo("2020.0.0");
	}

	@Test
	void usesCustomModuleIterationStartVersion() {

		ModuleIteration module = ReleaseTrains.DIJKSTRA.getModuleIteration(Projects.ELASTICSEARCH, Iteration.M1);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version).hasToString("1.0 M1 (Dijkstra)");
	}

	@Test
	void doesNotUseCustomIterationOnNonFirstiterations() {

		ModuleIteration module = ReleaseTrains.DIJKSTRA.getModuleIteration(Projects.ELASTICSEARCH, Iteration.RC1);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version).hasToString("1.0 RC1 (Dijkstra)");
	}

	@Test
	void rendersDescriptionCorrectly() {

		ModuleIteration module = ReleaseTrains.DIJKSTRA.getModuleIteration(Projects.ELASTICSEARCH, Iteration.M1);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version.getDescription()).isEqualTo("Dijkstra M2");
	}

	@Test
	void rendersShortBomAndReleaseMilestoneVersions() {

		TrainIteration iteration = new TrainIteration(ReleaseTrains.ULLMAN, Iteration.SR1);
		ModuleIteration module = iteration.getModule(Projects.BOM);

		GithubMilestone milestone = new GithubMilestone(module);
		assertThat(milestone.toMilestone().getTitle()).isEqualTo("2023.0.1");
		assertThat(milestone.toMilestone().getDescription()).isEqualTo("2023.0.1");
	}

	private void assertIterationVersion(Iteration iteration, String expected) {

		ModuleIteration module = ReleaseTrains.DIJKSTRA.getModuleIteration(Projects.COMMONS, iteration);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version).hasToString(expected);
	}
}
