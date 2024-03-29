/*
 * Copyright 2020-2022 the original author or authors.
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
import static org.assertj.core.api.Assumptions.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.release.AbstractIntegrationTests;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.Train;

/**
 * Integration tests for {@link DependencyOperations}.
 *
 * @author Mark Paluch
 */
@Disabled
class DependencyOperationsIntegrationTests extends AbstractIntegrationTests {

	private static final Train LATEST = ReleaseTrains.latest();

	@Autowired GitOperations git;
	@Autowired DependencyOperations operations;

	@BeforeAll
	static void beforeAll() {
		try {
			URL url = new URL("https://repo1.maven.org");
			URLConnection urlConnection = url.openConnection();
			urlConnection.connect();
			urlConnection.getInputStream().close();
		} catch (IOException e) {
			assumeThat(false).as("Test requires connectivity to Maven: " + e.toString()).isTrue();
		}
	}

	@BeforeEach
	void setUp() {
		git.checkout(ReleaseTrains.MOORE);
	}

	@Test
	void shouldDiscoverDependencyVersions() {
		assertThat(operations.getAvailableVersions(Dependencies.SPRING_HATEOAS)).isNotEmpty();
	}

	@Test
	void shouldReportExistingDependencyVersions() {
		assertThat(operations.getCurrentDependencies(LATEST.getSupportedProject(Projects.BUILD)).isEmpty()).isFalse();
	}

	@Test
	void shouldReportExistingOptionalDependencies() {

		assertThat(operations.getCurrentDependencies(LATEST.getSupportedProject(Projects.CASSANDRA)).getVersions())
				.hasSize(1);
		assertThat(operations.getCurrentDependencies(LATEST.getSupportedProject(Projects.MONGO_DB)).getVersions())
				.hasSize(2);
		assertThat(operations.getCurrentDependencies(LATEST.getSupportedProject(Projects.NEO4J)).getVersions()).hasSize(1);
	}

	@Test
	void getUpgradeProposals() {
		System.out
				.println(operations.getDependencyUpgradeProposals(LATEST.getSupportedProject(Projects.BUILD), Iteration.M1));
	}
}
