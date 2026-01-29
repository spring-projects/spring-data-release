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

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Milestone}.
 *
 * @author Mark Paluch
 */
class MilestoneUnitTests {

	@Test
	void isNearFuture() {

		Milestone unscheduled = new Milestone(1L, "", "", "open", null);
		Milestone future = new Milestone(1L, "", "", "open", Instant.now().plus(Duration.ofDays(1)));
		Milestone distantFuture = new Milestone(1L, "", "", "open", Instant.now().plus(Duration.ofDays(90)));

		assertThat(unscheduled.isNearFutureScheduled()).isFalse();
		assertThat(unscheduled.isReleaseSoon()).isFalse();

		assertThat(future.isNearFutureScheduled()).isTrue();
		assertThat(future.isReleaseSoon()).isTrue();

		assertThat(distantFuture.isNearFutureScheduled()).isFalse();
	}

	@Test
	void isNearPast() {

		Milestone unscheduled = new Milestone(1L, "", "", "open", null);
		Milestone nearPast = new Milestone(1L, "", "", "open", Instant.now().minus(Duration.ofDays(1)));
		Milestone distantPast = new Milestone(1L, "", "", "open", Instant.now().minus(Duration.ofDays(90)));

		assertThat(unscheduled.isNearPastScheduled()).isFalse();
		assertThat(unscheduled.isReleaseSoon()).isFalse();

		assertThat(nearPast.isNearPastScheduled()).isTrue();
		assertThat(nearPast.isReleaseSoon()).isTrue();

		assertThat(distantPast.isNearPastScheduled()).isFalse();
	}
}
