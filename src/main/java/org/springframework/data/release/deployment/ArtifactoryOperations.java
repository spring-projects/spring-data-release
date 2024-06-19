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
import java.util.Locale;

import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mark Paluch
 */
@Component
class ArtifactoryOperations {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AqlWriter aqlWriter;
	private final ArtifactoryClient client;

	public ArtifactoryOperations(DeploymentProperties properties, ArtifactoryClient client) {
		this.aqlWriter = new AqlWriter(properties.getCommercial(), objectMapper);
		this.client = client;
	}

	@SneakyThrows
	public void createArtifactoryRelease(ModuleIteration module) {

		String projectName = module.getProject().getName().toLowerCase(Locale.ROOT);
		String releaseName = String.format("TNZ-spring-data-%s-commercial", projectName);
		String version = ArtifactVersion.of(module).toString();
		String aql = aqlWriter.createFindAqlStatement(module);
		ArtifactoryReleaseBundle releaseBundle = new ArtifactoryReleaseBundle(releaseName, version, "aql",
				Collections.singletonMap("aql", aql));

		// TODO: Send REST request to create the release.
		// String json = objectMapper.writeValueAsString(releaseBundle);

		System.out.println(releaseName);
		System.out.println(objectMapper.writeValueAsString(releaseBundle));
		System.out.println();
		System.out.println();

		/*DeploymentProperties.Authentication authentication = properties.getAuthentication(module);

		client.createRelease(releaseBundle, authentication); */
	}
}
