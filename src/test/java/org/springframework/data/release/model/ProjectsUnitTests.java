/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * @author Mark Paluch
 */
class ProjectsUnitTests {

	@Test
	void considersSupportStatus() {

		assertThat(Projects.matches(Projects.SOLR, SupportStatus.OSS)).isFalse();
		assertThat(Projects.matches(Projects.SOLR, SupportStatus.EOL)).isTrue();
		assertThat(Projects.matches(Projects.GEODE, SupportStatus.OSS)).isFalse();
		assertThat(Projects.matches(Projects.GEODE, SupportStatus.COMMERCIAL)).isTrue();
	}

	@Test
	void shouldReturnProjectsWithFilter() {

		assertThat(Projects.all(SupportStatus.OSS)).doesNotContain(Projects.GEODE).doesNotContain(Projects.SOLR)
				.doesNotContain(Projects.RELEASE).contains(Projects.JPA);
	}
}
