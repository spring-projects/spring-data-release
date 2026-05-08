/*
 * Copyright 2015-2022 the original author or authors.
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
package org.springframework.data.release.build;

import static org.assertj.core.api.Assertions.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.data.release.model.Hotfix;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.Phase;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.TrainIteration;

/**
 * Unit tests for {@link UpdateInformation}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
class UpdateInformationUnitTests {

	TrainIteration hopperM1 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.M1, Hotfix.none());
	TrainIteration hopperSr1 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.none());
	TrainIteration hopperSr1HF5 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.of(5));

	@Test
	void rejectsNullTrainIteration() {
		Assertions.assertThatIllegalArgumentException().isThrownBy(() -> UpdateInformation.of(null, Phase.CLEANUP));
	}

	@Test
	void rejectsNullPhase() {
		Assertions.assertThatIllegalArgumentException().isThrownBy(() -> UpdateInformation.of(hopperM1, null));
	}

	@Test
	void calculatesProjectVersionToSetCorrectly() {

		UpdateInformation updateInformation = UpdateInformation.of(hopperM1, Phase.PREPARE);
		assertThat(updateInformation.getProjectVersionToSet(Projects.JPA).toString()).isEqualTo("2.4.0-M1");

		updateInformation = UpdateInformation.of(hopperM1, Phase.CLEANUP);
		assertThat(updateInformation.getProjectVersionToSet(Projects.JPA).toString()).isEqualTo("2.4.0-SNAPSHOT");
	}

	@Test // GH-146
	void calculatesProjectVersionToSetCorrectlySR1() {

		UpdateInformation updateInformation = UpdateInformation.of(hopperSr1, Phase.PREPARE);
		assertThat(updateInformation.getProjectVersionToSet(Projects.JPA).toString()).isEqualTo("2.4.1");

		updateInformation = UpdateInformation.of(hopperSr1, Phase.CLEANUP);
		assertThat(updateInformation.getProjectVersionToSet(Projects.JPA).toString()).isEqualTo("2.4.2-SNAPSHOT");
	}

	@Test // GH-146
	void calculatesProjectVersionToSetCorrectlySR1HF1() {

		UpdateInformation updateInformation = UpdateInformation.of(hopperSr1HF5, Phase.PREPARE);
		assertThat(updateInformation.getProjectVersionToSet(Projects.JPA).toString()).isEqualTo("2.4.1.5");

		updateInformation = UpdateInformation.of(hopperSr1HF5, Phase.CLEANUP);
		assertThat(updateInformation.getProjectVersionToSet(Projects.JPA).toString()).isEqualTo("2.4.1.6-SNAPSHOT");
	}

	@Test
	void noReposContainedForMilestoneRelease() {

		UpdateInformation updateInformation = UpdateInformation.of(hopperM1, Phase.PREPARE);

		assertThat(updateInformation.getRepositories().isEmpty());
	}

	@Test
	void noReposContainedForGaRelease() {

		UpdateInformation updateInformation = UpdateInformation
				.of(new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none()),
				Phase.PREPARE);

		assertThat(updateInformation.getRepositories()).isEmpty();

		updateInformation = UpdateInformation.of(new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none()),
				Phase.PREPARE);

		assertThat(updateInformation.getRepositories()).isEmpty();
	}

	@Test
	void cleanupSetsMilestoneAndSnapshotRepos() {

		UpdateInformation updateInformation = UpdateInformation
				.of(new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none()),
				Phase.CLEANUP);

		assertThat(updateInformation.getRepositories()).contains(Repository.MILESTONE, Repository.SNAPSHOT);
	}

	@Test // #155
	void calculatesProjectCalverVersionToSetCorrectly() {

		TrainIteration ockhamGa = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none());

		assertThat(UpdateInformation.of(ockhamGa, Phase.CLEANUP).getProjectVersionToSet(Projects.BOM).toString())
				.isEqualTo("2020.1.0-SNAPSHOT");
		assertThat(UpdateInformation.of(ockhamGa, Phase.MAINTENANCE).getProjectVersionToSet(Projects.BOM).toString())
				.isEqualTo("2020.0.1-SNAPSHOT");
	}

	@Test // GH-146
	void calculatesProjectCalverVersionWithHotfixToSetCorrectly() {

		TrainIteration ockhamGa = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.of(5));

		assertThat(UpdateInformation.of(ockhamGa, Phase.CLEANUP).getProjectVersionToSet(Projects.BOM).toString())
				.isEqualTo("2020.0.1.6-SNAPSHOT");
		assertThat(UpdateInformation.of(ockhamGa, Phase.MAINTENANCE).getProjectVersionToSet(Projects.BOM).toString())
				.isEqualTo("2020.0.1.6-SNAPSHOT");
	}

	@Test // #22
	void returnsCorrectReleaseTrainVersions() {

		TrainIteration ga = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none());
		TrainIteration sr1 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.none());

		assertThat(UpdateInformation.of(ga, Phase.PREPARE).getReleaseTrainVersion()).isEqualTo("2020.0.0");
		assertThat(UpdateInformation.of(hopperM1, Phase.PREPARE).getReleaseTrainVersion()).isEqualTo("2020.0.0-M1");
		assertThat(UpdateInformation.of(sr1, Phase.PREPARE).getReleaseTrainVersion()).isEqualTo("2020.0.1");
	}

	@Test // #155
	void returnsCorrectReleaseTrainCalverVersions() {

		TrainIteration ockhamGa = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none());
		TrainIteration ockhamM1 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.M1, Hotfix.none());
		TrainIteration ockhamSr1 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.none());
		TrainIteration ockhamSr1Hf5 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.of(5));

		assertThat(UpdateInformation.of(ockhamGa, Phase.PREPARE).getReleaseTrainVersion()).isEqualTo("2020.0.0");
		assertThat(UpdateInformation.of(ockhamM1, Phase.PREPARE).getReleaseTrainVersion()).isEqualTo("2020.0.0-M1");
		assertThat(UpdateInformation.of(ockhamSr1, Phase.PREPARE).getReleaseTrainVersion()).isEqualTo("2020.0.1");
		assertThat(UpdateInformation.of(ockhamSr1Hf5, Phase.PREPARE).getReleaseTrainVersion()).isEqualTo("2020.0.1.5");
	}

	@Test // #155
	void returnsCorrectCleanupReleaseTrainCalverVersions() {

		TrainIteration ockhamGa = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none());
		TrainIteration ockhamM1 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.M1, Hotfix.none());
		TrainIteration ockhamSr1 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.none());
		TrainIteration ockhamSr1Hf5 = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.SR1, Hotfix.of(5));

		assertThat(UpdateInformation.of(ockhamGa, Phase.CLEANUP).getReleaseTrainVersion()).isEqualTo("2020.1.0-SNAPSHOT");
		assertThat(UpdateInformation.of(ockhamM1, Phase.CLEANUP).getReleaseTrainVersion()).isEqualTo("2020.0.0-SNAPSHOT");
		assertThat(UpdateInformation.of(ockhamSr1, Phase.CLEANUP).getReleaseTrainVersion()).isEqualTo("2020.0.2-SNAPSHOT");
		assertThat(UpdateInformation.of(ockhamSr1Hf5, Phase.CLEANUP).getReleaseTrainVersion())
				.isEqualTo("2020.0.0.6-SNAPSHOT");
	}

	@Test // #155
	void returnsCorrectMaintenanceReleaseTrainCalverVersions() {

		TrainIteration ockhamGa = new TrainIteration(ReleaseTrains.OCKHAM, Iteration.GA, Hotfix.none());

		assertThat(UpdateInformation.of(ockhamGa, Phase.MAINTENANCE).getReleaseTrainVersion())
				.isEqualTo("2020.0.1-SNAPSHOT");
	}
}
