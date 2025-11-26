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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;
import static org.springframework.data.release.model.Projects.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.release.AbstractIntegrationTests;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.TestReleaseTrains;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Disabled
class GitOperationsIntegrationTests extends AbstractIntegrationTests {

	private static Train LATEST = ReleaseTrains.latest();

	@Autowired GitOperations gitOperations;

	@BeforeAll
	static void beforeClass() {

		try {
			URL url = new URL("https://github.com");
			URLConnection urlConnection = url.openConnection();
			urlConnection.connect();
			urlConnection.getInputStream().close();
		} catch (IOException e) {
			assumeThat(false).as("Test requires connectivity to GitHub:" + e.toString()).isTrue();
		}
	}

	@Test
	void updatesGitRepositories() throws Exception {
		gitOperations.update(ReleaseTrains.OCKHAM);
	}

	@Test
	void showTags() throws Exception {

		gitOperations.update(TestReleaseTrains.SAMPLE);

		assertThat(gitOperations.getTags(LATEST.getSupportedProject(BUILD)).asList()).isNotEmpty();
	}

	@Test
	void foo() throws Exception {
		gitOperations.update(TestReleaseTrains.SAMPLE);
	}

	@Test
	void obtainsVersionTagsForRepoThatAlsoHasOtherTags() {
		gitOperations.getTags(LATEST.getSupportedProject(MONGO_DB));
	}

	@Test
	void shouldDeterminePreviousIterationFromGA() throws Exception {

		TrainIteration OCKHAMRc1 = gitOperations.getPreviousIteration(ReleaseTrains.OCKHAM.getIteration(Iteration.GA));

		assertThat(OCKHAMRc1.getTrain()).isEqualTo(ReleaseTrains.OCKHAM);
		assertThat(OCKHAMRc1.getIteration()).isEqualTo(Iteration.GA);

		TrainIteration OCKHAMM1 = gitOperations.getPreviousIteration(ReleaseTrains.OCKHAM.getIteration(Iteration.RC1));

		assertThat(OCKHAMM1.getTrain()).isEqualTo(ReleaseTrains.OCKHAM);
		assertThat(OCKHAMM1.getIteration()).isEqualTo(Iteration.M1);

		TrainIteration OCKHAMSR9 = gitOperations.getPreviousIteration(ReleaseTrains.OCKHAM.getIteration(Iteration.SR10));

		assertThat(OCKHAMSR9.getTrain()).isEqualTo(ReleaseTrains.OCKHAM);
		assertThat(OCKHAMSR9.getIteration()).isEqualTo(Iteration.SR9);
	}

	@Test
	void shouldDeterminePreviousIterationFromSR() {

		TrainIteration OCKHAMGA = gitOperations.getPreviousIteration(ReleaseTrains.OCKHAM.getIteration(Iteration.SR1));

		assertThat(OCKHAMGA.getTrain()).isEqualTo(ReleaseTrains.OCKHAM);
		assertThat(OCKHAMGA.getIteration()).isEqualTo(Iteration.GA);

		TrainIteration OCKHAMSR9 = gitOperations.getPreviousIteration(ReleaseTrains.OCKHAM.getIteration(Iteration.SR10));

		assertThat(OCKHAMSR9.getTrain()).isEqualTo(ReleaseTrains.OCKHAM);
		assertThat(OCKHAMSR9.getIteration()).isEqualTo(Iteration.SR9);
	}

}
