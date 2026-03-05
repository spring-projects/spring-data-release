/*
 * Copyright 2014-2025 the original author or authors.
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
package org.springframework.data.release.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ArtifactVersion}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
class ArtifactVersionUnitTests {

	@Test
	void parsesReleaseVersionCorrectly() {

		assertThat(ArtifactVersion.of("7.2.4.Final").isReleaseVersion()).isTrue();

		assertThat(ArtifactVersion.of("1.4.5").isReleaseVersion()).isTrue();
		assertThat(ArtifactVersion.of("1.4.5").getNextDevelopmentVersion()).isEqualTo(ArtifactVersion.of("1.4.6-SNAPSHOT"));

		assertThat(ArtifactVersion.of("1.4.5").isReleaseVersion()).isTrue();
		assertThat(ArtifactVersion.of("1.4.5").getNextDevelopmentVersion()).isEqualTo(ArtifactVersion.of("1.4.6-SNAPSHOT"));
	}

	@Test
	void parsesSnapshotCorrectly() {

		assertThat(ArtifactVersion.of("1.4.5-SNAPSHOT").isReleaseVersion()).isFalse();
		assertThat(ArtifactVersion.of("1.4.5-SNAPSHOT").isSnapshotVersion()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(
			strings = { "12.1.3.0_special_74723", "9.4-1205-jdbc42", "13.3.1.jre8-preview", "11.1.0-SNAPSHOT.jre8-preview",
					"1.5.1-native-mt", "0.0.0.1.3.0-HATEOAS-1417-SNAPSHOT.1", "0.1.0.20091028042923", "1.0" })
	void parsesVersionsCorrectly(String version) {

		ArtifactVersion artifactVersion = ArtifactVersion.of(version);
		assertThat(artifactVersion).hasToString(version);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1.4.5.M1", "1.0.0-alpha-1", "2.1.0-alpha0", "2.1.0-alpha0", "2.0.64-beta" })
	void parsesMilestoneCorrectly(String version) {

		ArtifactVersion artifactVersion = ArtifactVersion.of(version);
		assertThat(artifactVersion.isReleaseVersion()).isFalse();
		assertThat(artifactVersion.isMilestoneVersion()).isTrue();
		assertThat(artifactVersion).hasToString(version);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1.15.0-rc1", "1.15.0-rc", "1.15.0-RC1" })
	void parsesReleaseCandidateCorrectly(String version) {

		ArtifactVersion artifactVersion = ArtifactVersion.of(version);

		assertThat(artifactVersion.isReleaseVersion()).isFalse();
		assertThat(artifactVersion.isMilestoneVersion()).isFalse();
		assertThat(artifactVersion.isReleaseCandidateVersion()).isTrue();
		assertThat(artifactVersion).hasToString(version);
	}

	@Test
	void createsReleaseVersionByDefault() {

		ArtifactVersion version = ArtifactVersion.of(Version.of(1, 4, 5));

		assertThat(version.isReleaseVersion()).isTrue();
		assertThat(version).hasToString("1.4.5.RELEASE");
	}

	@Test
	void createsMilestoneVersionFromIteration() {

		IterationVersion oneFourMilestoneOne = new SimpleIterationVersion(Version.of(1, 4), Iteration.M1);
		ArtifactVersion version = ArtifactVersion.of(oneFourMilestoneOne);

		assertThat(version.isMilestoneVersion()).isTrue();
		assertThat(version).hasToString("1.4.0.M1");
	}

	@Test
	void createsReleaseVersionFromIteration() {

		IterationVersion oneFourGA = new SimpleIterationVersion(Version.of(1, 4), Iteration.GA);
		ArtifactVersion version = ArtifactVersion.of(oneFourGA);

		assertThat(version.isReleaseVersion()).isTrue();
		assertThat(version).hasToString("1.4.0.RELEASE");
	}

	@Test
	void createsServiceReleaseVersionFromIteration() {

		IterationVersion oneFourServiceReleaseTwo = new SimpleIterationVersion(Version.of(1, 4), Iteration.SR2);
		ArtifactVersion version = ArtifactVersion.of(oneFourServiceReleaseTwo);

		assertThat(version.isReleaseVersion()).isTrue();
		assertThat(version).hasToString("1.4.2.RELEASE");
	}

	@Test
	void returnsNextMinorSnapshotVersionForGARelease() {

		ArtifactVersion version = ArtifactVersion.of("1.5.0").getNextDevelopmentVersion();

		assertThat(version.isMilestoneVersion()).isFalse();
		assertThat(version.isReleaseVersion()).isFalse();
		assertThat(version).isEqualTo(ArtifactVersion.of("1.6.0-SNAPSHOT"));
	}

	@Test
	void ordersCorrectly() {

		assertThat(ArtifactVersion.of("1.9.0.RELEASE")).isLessThan(ArtifactVersion.of("1.10.0.RELEASE"));
		assertThat(ArtifactVersion.of("1.9.25.1.RELEASE")).isLessThan(ArtifactVersion.of("1.9.25.2.RELEASE"));
		assertThat(ArtifactVersion.of("1.9.10.RELEASE")).isLessThan(ArtifactVersion.of("1.9.11.RELEASE"))
				.isGreaterThan(ArtifactVersion.of("1.9.2.RELEASE"));
		assertThat(ArtifactVersion.of("1.9.0-M2")).isLessThan(ArtifactVersion.of("1.9.0-M3"))
				.isGreaterThan(ArtifactVersion.of("1.9.0-M1"));
		assertThat(ArtifactVersion.of("1.9.0-M2")).isGreaterThan(ArtifactVersion.of("1.9.0-SNAPSHOT"))
				.isLessThan(ArtifactVersion.of("1.9.0-RC1")).isLessThan(ArtifactVersion.of("1.9.0"));
	}

	@Test
	void returnsCorrectBugfixVersions() {

		assertThat(ArtifactVersion.of("1.0.0").getNextBugfixVersion()).isEqualTo(ArtifactVersion.of("1.0.1-SNAPSHOT"));
		assertThat(ArtifactVersion.of("1.0.0-M1").getNextBugfixVersion()).isEqualTo(ArtifactVersion.of("1.0.0-SNAPSHOT"));
		assertThat(ArtifactVersion.of("1.0.1").getNextBugfixVersion()).isEqualTo(ArtifactVersion.of("1.0.2-SNAPSHOT"));
	}
}
