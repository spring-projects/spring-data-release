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

import lombok.SneakyThrows;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mark Paluch
 */
@Component
class ArtifactoryOperations {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AqlWriter aqlWriter;
	private final DeploymentProperties.Authentication authentication;
	private final ArtifactoryClient client;

	public ArtifactoryOperations(DeploymentProperties properties, ArtifactoryClient client) {
		this.authentication = properties.getCommercial();
		this.aqlWriter = new AqlWriter(authentication, objectMapper);
		this.client = client;
	}

	@SneakyThrows
	public void createArtifactoryRelease(ModuleIteration module) {

		ArtifactoryReleaseBundle releaseBundle = createReleaseBundle(module);
		client.createRelease(module.getProject().getName(), releaseBundle, authentication);
	}

	@SneakyThrows
	public void createArtifactoryReleaseAggregator(TrainIteration train) {

		ModuleIteration bom = train.getModule(Projects.BOM);
		String releaseName = "TNZ-spring-data-commercial-release";
		String version = ArtifactVersion.of(bom).toString();

		List<ArtifactoryReleaseBundle> releaseBundles = train.stream().map(this::createReleaseBundleRef)
				.collect(Collectors.toList());
		ArtifactoryReleaseBundle aggregator = new ArtifactoryReleaseBundle(releaseName, version, null, null,
				"release_bundles", Collections.singletonMap("release_bundles", releaseBundles));

		client.createRelease(train.toString(), aggregator, authentication);
	}

	public void distributeArtifactoryReleaseAggregator(TrainIteration train) {

		ModuleIteration bom = train.getModule(Projects.BOM);
		String releaseName = "TNZ-spring-data-commercial-release";
		String version = ArtifactVersion.of(bom).toString();

		client.distributeRelease(train, releaseName, version, authentication);
	}

	ArtifactoryReleaseBundle createReleaseBundle(ModuleIteration module) {

		String releaseName = getReleaseName(module);
		String version = ArtifactVersion.of(module).toString();
		String aql = aqlWriter.createFindAqlStatement(module);

		return new ArtifactoryReleaseBundle(releaseName, version, null, null, "aql", Collections.singletonMap("aql", aql));
	}

	ArtifactoryReleaseBundle createReleaseBundleRef(ModuleIteration module) {

		String releaseName = getReleaseName(module);
		String version = ArtifactVersion.of(module).toString();
		String aql = aqlWriter.createFindAqlStatement(module);

		return new ArtifactoryReleaseBundle(releaseName, version, "spring", "spring-release-bundles-v2", null, null);
	}

	private static String getReleaseName(ModuleIteration module) {
		String projectName = module.getProject().getName().toLowerCase(Locale.ROOT);
		return String.format("TNZ-spring-data-%s-commercial", projectName);
	}

}
