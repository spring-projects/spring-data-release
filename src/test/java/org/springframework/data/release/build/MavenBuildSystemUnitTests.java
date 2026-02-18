/*
 * Copyright 2022-present the original author or authors.
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

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.release.build.Pom.RepositoryElementFactory;
import org.springframework.data.release.git.Branch;
import org.xmlbeam.XBProjector;

/**
 * Unit tests for {@link MavenBuildSystem}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
class MavenBuildSystemUnitTests {

	XBProjector projector = new BuildConfiguration().projectionFactory();

	@Test
	void shouldAddLineBreaksAfterProcessing() throws Exception {

		ClassPathResource resource = new ClassPathResource("sample-pom.xml");

		try (InputStream is = resource.getInputStream()) {

			byte[] bytes = MavenBuildSystem.doWithProjection(projector, is, Pom.class, pom -> {});

			assertThat(new String(bytes)).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ IOUtils.LINE_SEPARATOR + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" ")
					.endsWith(IOUtils.LINE_SEPARATOR);
		}
	}

	@Test
	void shouldRemoveRepositories() throws Exception {

		ClassPathResource resource = new ClassPathResource("sample-pom.xml");

		try (InputStream is = resource.getInputStream()) {

			byte[] bytes = MavenBuildSystem.doWithProjection(projector, is, Pom.class, Pom::deleteRepositories);

			assertThat(new String(bytes)).contains("<repositories>").doesNotContain("<repository>");
		}
	}

	@Test
	void shouldAddRepositories() throws Exception {

		ClassPathResource resource = new ClassPathResource("sample-pom.xml");

		try (InputStream is = resource.getInputStream()) {

			byte[] bytes = MavenBuildSystem.doWithProjection(projector, is, Pom.class, pom -> {
				pom.deleteRepositories();
				pom.setRepositories(RepositoryElementFactory.of(Repository.SNAPSHOT, Repository.MILESTONE));
			});

			assertThat(new String(bytes)).containsSubsequence("repositories", "<id>spring-snapshot</id>", "<snapshots>",
					"<enabled>true</enabled>", "<releases>", "<enabled>false</enabled>", "spring-milestone");
		}
	}

	@Test
	void ghActionsRegexShouldCaptureVersion() {

		String workflow = """
			- name: Setup Java and Maven
			  uses: spring-projects/spring-data-build/actions/setup-maven@5.0.x
			- name: Deploy to Artifactory
			  uses: spring-projects/spring-data-build/actions/maven-artifactory-deploy@main
			- uses: actions/setup-java@v5.2.0
			  id: install-custom-java-version
			""";

		String result = MavenBuildSystem.updateGhActionReferencesToNewBranch(workflow, Branch.from("5.1.x"));

		assertThat(result)
			.contains("uses: spring-projects/spring-data-build/actions/setup-maven@5.1.x")
			.contains("uses: spring-projects/spring-data-build/actions/maven-artifactory-deploy@5.1.x")
			.contains("uses: actions/setup-java@v5.2.0");
	}

	@Test
	void updateGhActionWorkflowPushBranchesReplacesMainAndIssuePatternOnlyInBranchesLine() {

		String workflow = """
			on:
			  workflow_dispatch:
			  push:
			    branches: [ main, 'issue/**' ]
			permissions: read-all
			jobs:
			  build-java:
			    strategy:
			      matrix:
			        java-version: [ base, main ]
			        mongodb-version: [ 'latest', '8.2', '8.0', '7.0' ]
			""";

		String result = MavenBuildSystem.updateGhActionWorkflowPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("branches: [ 5.1.x, 'issue/5.1.x/**' ]");
		assertThat(result).contains("java-version: [ base, main ]");
	}

	@Test
	void updateGhActionWorkflowPushBranchesRemovesBranchesBetweenMainAndIssuePattern() {

		String workflow = """
			on:
			  push:
			    branches: [ main, 5.0.x, 4.5.x, 'issue/**' ]
			jobs:
			  build-java:
			""";

		String result = MavenBuildSystem.updateGhActionWorkflowPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("branches: [ 5.1.x, 'issue/5.1.x/**' ]");
		assertThat(result).doesNotContain("5.0.x").doesNotContain("4.5.x");
	}

	@Test
	void leavesGhActionWorkflowPushBranchesIfNotForIssue() {

		String workflow = """
			on:
			  push:
			    branches: [ 5.0.x, 4.5.x ]
			jobs:
			  build-java:
			""";

		String result = MavenBuildSystem.updateGhActionWorkflowPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("branches: [ 5.0.x, 4.5.x ]");
	}
}
