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
package org.springframework.data.release.utils;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VersionedStringComparator}.
 *
 * @author Mark Paluch
 */
class VersionedStringComparatorUnitTests {

	private final VersionedStringComparator comparator = VersionedStringComparator.INSTANCE;

	@Test
	void shouldSortUpgradeStatementsCorrectly() {

		List<String> statements = new ArrayList<>();
		statements.add("Upgrade to Project Reactor 2024.0.16");
		statements.add("Upgrade to Project Reactor 2024.0.1");
		statements.add("Upgrade to Micrometer 1.15.10");
		statements.add("Upgrade to Spring Framework 6.2.17");
		statements.add("Upgrade to Project Reactor 2025.0.4");
		statements.add("Upgrade to Micrometer 1.16.4");
		statements.add("Upgrade to Spring Framework 7.0.6");
		statements.add("Upgrade to Micrometer 1.17.0-M3");
		statements.add("Upgrade to Spring LDAP 4.1.0-M2");

		statements.sort(comparator);

		assertThat(statements).containsExactly( //
				"Upgrade to Micrometer 1.15.10", //
				"Upgrade to Micrometer 1.16.4", //
				"Upgrade to Micrometer 1.17.0-M3", //
				"Upgrade to Project Reactor 2024.0.1", //
				"Upgrade to Project Reactor 2024.0.16", //
				"Upgrade to Project Reactor 2025.0.4", //
				"Upgrade to Spring Framework 6.2.17", //
				"Upgrade to Spring Framework 7.0.6", //
				"Upgrade to Spring LDAP 4.1.0-M2" //
		);
	}

	@Test
	void shouldCompareSimpleVersionNumbers() {

		assertThat(comparator.compare("Version 1.2.3", "Version 1.2.4")).isNegative();
		assertThat(comparator.compare("Version 1.2.4", "Version 1.2.3")).isPositive();
		assertThat(comparator.compare("Version 1.2.3", "Version 1.2.3")).isZero();
	}

	@Test
	void shouldCompareMajorVersionDifferences() {

		assertThat(comparator.compare("Version 1.0.0", "Version 2.0.0")).isNegative();
		assertThat(comparator.compare("Version 7.0.6", "Version 6.2.17")).isPositive();
	}

	@Test
	void shouldCompareMinorVersionDifferences() {

		assertThat(comparator.compare("Version 1.15.10", "Version 1.16.4")).isNegative();
		assertThat(comparator.compare("Version 1.16.4", "Version 1.15.10")).isPositive();
	}

	@Test
	void shouldCompareYearBasedVersions() {

		assertThat(comparator.compare("Reactor 2024.0.16", "Reactor 2025.0.4")).isNegative();
		assertThat(comparator.compare("Reactor 2025.0.4", "Reactor 2024.0.16")).isPositive();
	}

	@Test
	void shouldHandlePreReleaseVersions() {

		// M (Milestone) comes before RC (Release Candidate)
		assertThat(comparator.compare("Version 1.17.0-M3", "Version 1.17.0-RC1")).isNegative();

		// RC comes before release version (no suffix)
		assertThat(comparator.compare("Version 1.17.0-RC1", "Version 1.17.0")).isNegative();

		// Pre-release comes before release
		assertThat(comparator.compare("Version 4.1.0-M2", "Version 4.1.0")).isNegative();
	}

	@Test
	void shouldCompareMilestoneNumbers() {

		assertThat("Version 1.17.0-M2").usingComparator(comparator).isLessThan("Version 1.17.0-M3");
		assertThat("Version 1.17.0-M10").usingComparator(comparator).isGreaterThan("Version 1.17.0-M3");

		assertThat("Version 1.17.0-RC1").usingComparator(comparator).isLessThan("Version 1.17.0-RC2");
		assertThat("Version 1.17.0-RC2").usingComparator(comparator).isGreaterThan("Version 1.17.0-RC1")
				.isGreaterThan("Version 1.17.0-M10");
	}

	@Test
	void shouldHandleSnapshotVersions() {

		assertThat("Version 1.17.0-SNAPSHOT").usingComparator(comparator).isLessThan("Version 1.17.0");
		assertThat("Version 1.17.0-RC1").usingComparator(comparator).isGreaterThan("Version 1.17.0-SNAPSHOT");
	}

	@Test
	void shouldCompareLexicographicallyWhenNoVersionPresent() {

		assertThat(comparator.compare("Upgrade to Spring LDAP", "Upgrade to Spring Framework")).isPositive();
		assertThat(comparator.compare("Upgrade to Micrometer", "Upgrade to Project Reactor")).isNegative();
	}

	@Test
	void shouldHandleDifferentTokenCounts() {

		assertThat(comparator.compare("Upgrade to Framework 1.0.0", "Upgrade to Framework 1.0.0 Beta")).isNegative();
		assertThat(comparator.compare("Upgrade to Framework", "Upgrade to Framework 1.0.0")).isNegative();
	}

	@Test
	void shouldHandleVersionsAtDifferentPositions() {

		// Different project names should be compared first
		assertThat(comparator.compare("Upgrade to ProjectA 2.0.0", "Upgrade to ProjectB 1.0.0")).isNegative();
	}

	@Test
	void shouldHandleTwoDigitVersionComponents() {

		assertThat(comparator.compare("Version 1.15.10", "Version 1.15.9")).isPositive();
		assertThat(comparator.compare("Version 6.2.17", "Version 6.2.9")).isPositive();
	}

	@Test
	void shouldHandleNullValues() {

		assertThat(comparator.compare(null, "Version 1.0.0")).isNegative();
		assertThat(comparator.compare("Version 1.0.0", null)).isPositive();
		assertThat(comparator.compare(null, null)).isZero();
	}

	@Test
	void shouldHandleEmptyStrings() {

		assertThat(comparator.compare("", "Version 1.0.0")).isNegative();
		assertThat(comparator.compare("Version 1.0.0", "")).isPositive();
		assertThat(comparator.compare("", "")).isZero();
	}

	@Test
	void shouldHandleTwoComponentVersions() {

		assertThat(comparator.compare("Version 1.0", "Version 1.1")).isNegative();
		assertThat(comparator.compare("Version 2024.0", "Version 2025.0")).isNegative();
	}

	@Test
	void shouldHandleFourComponentVersions() {

		assertThat("Version 1.2.10").usingComparator(comparator).isLessThan("Version 1.2.11").isLessThan("Version 1.2.99")
				.isGreaterThan("Version 1.2.9");
	}

	@Test
	void shouldCompareComplexPreReleaseIdentifiers() {

		assertThat(comparator.compare("Version 1.0.0-alpha", "Version 1.0.0-beta")).isNegative();
		assertThat(comparator.compare("Version 1.0.0-beta.2", "Version 1.0.0-beta.11")).isPositive();
	}

	@Test
	void shouldMaintainStabilityForEqualElements() {

		assertThat(comparator.compare("Upgrade to Framework 1.0.0", "Upgrade to Framework 1.0.0")).isZero();
		assertThat(comparator.compare("Same text", "Same text")).isZero();
	}
}
