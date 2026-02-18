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

		String result = InfrastructureOperations.updateGhActionReferencesToNewBranch(workflow, Branch.from("5.1.x"));

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

		String result = InfrastructureOperations.updateGhActionWorkflowPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("branches: [ 5.1.x, 'issue/5.1.x/**' ]");
		assertThat(result).contains("java-version: [ base, main ]");
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

		String result = InfrastructureOperations.updateGhActionWorkflowPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("branches: [ 5.1.x, 'issue/5.1.x/**' ]");
		assertThat(result).doesNotContain("5.0.x").doesNotContain("4.5.x");
	}

	@Test
	void leavesGhActionWorkflowPushBranchesIfNotForIssue() {

		String workflow = """
				on:
				  workflow_dispatch:
				  push:
				    branches: [ 5.0.x, 4.5.x ]
				jobs:
				  build-java:
				""";

		String result = InfrastructureOperations.updateGhActionWorkflowPushBranches(workflow, Branch.from("5.1.x"));

		assertThat(result).contains("workflow_dispatch").contains("branches: [ 5.0.x, 4.5.x ]");
	}
}
