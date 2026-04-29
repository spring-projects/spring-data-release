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

		assertIterationVersion(Iteration.M1, "2.4 M1 (2020.0.0)");
		assertIterationVersion(Iteration.RC1, "2.4 RC1 (2020.0.0)");
		assertIterationVersion(Iteration.GA, "2.4 GA (2020.0.0)");

		assertIterationVersion(Iteration.SR1, "2.4.1 (2020.0.1)");
		assertIterationVersion(Iteration.SR2, "2.4.2 (2020.0.2)");
		assertIterationVersion(Iteration.SR3, "2.4.3 (2020.0.3)");
		assertIterationVersion(Iteration.SR4, "2.4.4 (2020.0.4)");
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

		ModuleIteration module = ReleaseTrains.OCKHAM.getModuleIteration(Projects.ELASTICSEARCH, Iteration.M1);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version).hasToString("4.1 M1 (2020.0.0)");
	}

	@Test
	void doesNotUseCustomIterationOnNonFirstiterations() {

		ModuleIteration module = ReleaseTrains.OCKHAM.getModuleIteration(Projects.ELASTICSEARCH, Iteration.RC1);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version).hasToString("4.1 RC1 (2020.0.0)");
	}

	@Test
	void rendersDescriptionCorrectly() {

		ModuleIteration module = ReleaseTrains.OCKHAM.getModuleIteration(Projects.ELASTICSEARCH, Iteration.M1);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version.getDescription()).isEqualTo("2020.0.0-M1");
	}

	@Test
	void rendersShortBomAndReleaseMilestoneVersions() {

		TrainIteration iteration = new TrainIteration(ReleaseTrains.VAUGHAN, Iteration.SR1);
		ModuleIteration module = iteration.getModule(Projects.BOM);

		GithubMilestone milestone = new GithubMilestone(module);
		assertThat(milestone.toMilestone().getTitle()).isEqualTo("2023.1.1");
		assertThat(milestone.toMilestone().getDescription()).isEqualTo("2023.1.1");
	}

	private void assertIterationVersion(Iteration iteration, String expected) {

		ModuleIteration module = ReleaseTrains.OCKHAM.getModuleIteration(Projects.COMMONS, iteration);

		GithubMilestone version = new GithubMilestone(module);
		assertThat(version).hasToString(expected);
	}
}
