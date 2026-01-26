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
package org.springframework.data.release.model;

import java.util.Locale;

/**
 * Naming strategy for Spring Data Projects.
 *
 * @author Mark Paluch
 */
enum SpringDataNamingStrategy implements GitHubNamingStrategy, ArtifactNamingStrategy {

	INSTANCE;

	public static ArtifactCoordinates SPRING_DATA = ArtifactCoordinates.forGroupId("org.springframework.data");

	public static ArtifactCoordinates SPRING_DATA_BUILD = ArtifactCoordinates
			.forGroupId("org.springframework.data.build");

	@Override
	public String getOwner(String projectName, SupportStatus supportStatus) {
		return "spring-projects";
	}

	@Override
	public String getRepository(String projectName, SupportStatus supportStatus) {

		String name = projectName.toLowerCase(Locale.ROOT);
		return (supportStatus.isCommercial() ? "spring-data-%s-commercial" : "spring-data-%s").formatted(name);
	}

	@Override
	public String getArtifactName(String projectName) {
		String name = projectName.toLowerCase(Locale.ROOT);
		return "spring-data-%s".formatted(name);
	}

	@Override
	public String getFullName(String projectName) {
		return "Spring Data %s".formatted(projectName);
	}

}
