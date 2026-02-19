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
package org.springframework.data.release.infra;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.springframework.data.release.git.Branch;

/**
 * Unit tests for {@link InfrastructureOperations}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
class InfrastructureOperationsUnitTests {

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

		String result = InfrastructureOperations.updateActionRefs(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("uses: spring-projects/spring-data-build/actions/setup-maven@5.1.x")
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

		String result = InfrastructureOperations.updateOnPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("branches: [ 5.1.x, 'issue/5.1.x/**' ]");
		assertThat(result).contains("java-version: [ base, main ]");
	}

	@Test
	void updatesBranchesToMainCorrectly() {

		String workflow = """
				on:
				  workflow_dispatch:
				  push:
				    branches: [ main, 'issue/**' ]
				""";

		String result = InfrastructureOperations.updateOnPushBranches(workflow, Branch.MAIN);

		assertThat(result).contains("branches: [ main, 'issue/**' ]");
	}

	@Test
	void updatesBomBranchesCorrectly() {

		String workflow = """
				on:
				  push:
				    branches: [ main, '2025.1.x', '2025.0.x' ]
				""";

		String result = InfrastructureOperations.updateOnPushBranches(workflow, Branch.from("2025.1.x"));

		assertThat(result).contains("branches: [ 2025.1.x ]");
	}

	@Test
	void updateGhActionWorkflowPushBranchesRemovesBranchesBetweenMainAndIssuePattern() {

		String workflow = """
				on:
				  push:
				    branches: [  main ,  5.0.x,  4.5.x, 'issue/**' ]
				jobs:
				  build-java:
				""";

		String result = InfrastructureOperations.updateOnPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("branches: [ 5.1.x, 'issue/5.1.x/**' ]");
		assertThat(result).doesNotContain("5.0.x").doesNotContain("4.5.x");
	}

	@Test
	void updatesName() {

		String workflow = """
				name: CI Build
				on:
				  workflow_dispatch:
				  push:
				    branches: [ 5.0.x, 4.5.x ]
				""";

		String result = InfrastructureOperations.updateActionName(workflow, "CI 5.1.x");

		assertThat(result).contains("name: CI 5.1.x");
	}
}
