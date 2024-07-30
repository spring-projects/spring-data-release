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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.release.build.MavenArtifact;
import org.springframework.data.release.model.ArtifactCoordinate;
import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.data.release.model.ModuleIteration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Mark Paluch
 */
public class AqlWriter {

	private final DeploymentProperties.Authentication targetServer;

	private final ObjectMapper objectMapper;

	public AqlWriter(DeploymentProperties.Authentication targetServer, ObjectMapper objectMapper) {
		this.targetServer = targetServer;
		this.objectMapper = objectMapper;
	}

	/**
	 * Create an AQL statement to find all associated artifacts with {@link ModuleIteration}.
	 *
	 * @param module
	 * @return
	 */
	@SneakyThrows
	public String createFindAqlStatement(ModuleIteration module) {

		MavenArtifact mavenArtifact = new MavenArtifact(module);
		Set<Map<String, Map<String, String>>> matches = new LinkedHashSet<>();

		matches.add(createAqlFilter(mavenArtifact.toArtifactCoordinate(), module));

		module.getProject().doWithAdditionalArtifacts(artifactCoordinate -> {
			matches.add(createAqlFilter(artifactCoordinate, module));
		});

		Map<String, String> repo = Collections.singletonMap("repo", targetServer.getTargetRepository());
		Map<String, Object> orMatches = Collections.singletonMap("$or", matches);

		return String.format("items.find(%s, %s)", objectMapper.writeValueAsString(repo),
				objectMapper.writeValueAsString(orMatches));
	}

	private static Map<String, Map<String, String>> createAqlFilter(ArtifactCoordinate coordinate,
			ModuleIteration module) {

		ArtifactVersion version = ArtifactVersion.of(module);
		String groupIdPath = coordinate.getGroupId();
		String modulePath = String.format("%s/%s/%s", groupIdPath.replace('.', '/'), coordinate.getArtifactId(), version);

		return Collections.singletonMap("path", Collections.singletonMap("$match", modulePath));
	}

}
