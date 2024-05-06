/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.data.release.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.TrainIteration;

/**
 * Unit tests for {@link ArtifactoryOperations}.
 *
 * @author Mark Paluch
 */
class ArtifactoryOperationsUnitTests {

	DeploymentProperties properties = new DeploymentProperties();

	ArtifactoryOperations operations;

	@BeforeEach
	void setUp() {

		DeploymentProperties.Authentication authentication = new DeploymentProperties.Authentication();
		authentication.setProject("spring");
		authentication.setTargetRepository("spring-enterprise-maven-prod-local");

		properties.setCommercial(authentication);

		operations = new ArtifactoryOperations(properties, mock(ArtifactoryClient.class));
	}

	@Test
	void shouldCreateReleaseBundle() {

		TrainIteration iteration = ReleaseTrains.ULLMAN.getIteration(Iteration.SR1);
		ModuleIteration module = iteration.getModule(Projects.COMMONS);

		ArtifactoryReleaseBundle releaseBundle = operations.createReleaseBundle(module);

		assertThat(releaseBundle.getName()).isEqualTo("TNZ-spring-data-commons-commercial");
		assertThat(releaseBundle.getVersion()).isEqualTo("3.1.1");
		assertThat(releaseBundle.getSource_type()).isEqualTo("aql");

		assertThat(releaseBundle.getSource()).isInstanceOf(Map.class);

		String aql = ((Map<String, String>) releaseBundle.getSource()).get("aql");
		assertThat(aql).contains(
				"items.find({\"repo\":\"spring-enterprise-maven-prod-local\"}, {\"$or\":[{\"path\":{\"$match\":\"org/springframework/data/spring-data-commons/3.1.1\"}}]})");
	}

	@Test
	void shouldCreateMultiModuleBundle() {

		TrainIteration iteration = ReleaseTrains.ULLMAN.getIteration(Iteration.SR1);
		ModuleIteration module = iteration.getModule(Projects.RELATIONAL);

		ArtifactoryReleaseBundle releaseBundle = operations.createReleaseBundle(module);

		assertThat(releaseBundle.getName()).isEqualTo("TNZ-spring-data-relational-commercial");
		assertThat(releaseBundle.getVersion()).isEqualTo("3.1.1");
		assertThat(releaseBundle.getSource_type()).isEqualTo("aql");

		assertThat(releaseBundle.getSource()).isInstanceOf(Map.class);

		String aql = ((Map<String, String>) releaseBundle.getSource()).get("aql");
		assertThat(aql).contains("items.find({\"repo\":\"spring-enterprise-maven-prod-local\"}");
		assertThat(aql).contains("\"$match\":\"org/springframework/data/spring-data-relational-parent/3.1.1");
		assertThat(aql).contains("\"$match\":\"org/springframework/data/spring-data-jdbc/3.1.1");
		assertThat(aql).contains("\"$match\":\"org/springframework/data/spring-data-r2dbc/3.1.1");
		assertThat(aql).contains("\"$match\":\"org/springframework/data/spring-data-jdbc-distribution/3.1.1");
	}

	@Test
	void shouldCreateBomBundle() {

		TrainIteration iteration = ReleaseTrains.ULLMAN.getIteration(Iteration.SR1);
		ModuleIteration module = iteration.getModule(Projects.BOM);

		ArtifactoryReleaseBundle releaseBundle = operations.createReleaseBundle(module);

		assertThat(releaseBundle.getName()).isEqualTo("TNZ-spring-data-bom-commercial");
		assertThat(releaseBundle.getVersion()).isEqualTo("2023.0.1");
		assertThat(releaseBundle.getSource_type()).isEqualTo("aql");

		assertThat(releaseBundle.getSource()).isInstanceOf(Map.class);

		String aql = ((Map<String, String>) releaseBundle.getSource()).get("aql");
		assertThat(aql).contains(
				"items.find({\"repo\":\"spring-enterprise-maven-prod-local\"}, {\"$or\":[{\"path\":{\"$match\":\"org/springframework/data/spring-data-bom/2023.0.1\"}}]})");
	}
}
