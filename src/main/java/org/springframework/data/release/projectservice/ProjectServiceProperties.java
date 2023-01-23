/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.data.release.projectservice;

import lombok.Setter;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.release.model.Project;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.util.UriTemplate;

/**
 * Configuration properties for the Projects Service instance to talk to.
 *
 * @author Mark Paluch
 */
@Component
@ConfigurationProperties(prefix = "project-service")
class ProjectServiceProperties implements ProjectServiceResources {

	@Setter String key;
	@Setter String base = "https://api.spring.io/";

	/**
	 * Returns the URI to the resource exposing the project releases for the given {@link Project}.
	 *
	 * @param project must not be {@literal null}.
	 * @return
	 */
	public URI getProjectReleasesResource(Project project) {

		Assert.notNull(project, "Project must not be null!");

		return new UriTemplate(base + "projects/{project}/releases").expand(ProjectPaths.getProjectPathSegment(project));
	}

	/**
	 * Returns the URI to the resource exposing the project generations for the given {@link Project}.
	 *
	 * @param project must not be {@literal null}.
	 * @return
	 */
	public URI getProjectGenerationsResource(Project project) {

		Assert.notNull(project, "Project must not be null!");

		return new UriTemplate(base + "projects/{project}/generations").expand(ProjectPaths.getProjectPathSegment(project));
	}

	/**
	 * Returns the URI to the resource exposing the project version for the given {@link Project} and version
	 * {@link String}.
	 *
	 * @param project must not be {@literal null}.
	 * @param version must not be {@literal null}.
	 * @return
	 */
	public URI getProjectReleaseResource(Project project, String version) {

		Assert.notNull(project, "Project  must not be null!");
		Assert.hasText(version, "Version must not be null!");

		return new UriTemplate(base + "projects/{project}/releases/{version}")
				.expand(ProjectPaths.getProjectPathSegment(project), version);
	}

	/**
	 * Returns the {@link URI} to the resource exposing the project version for the given {@link MaintainedVersion}.
	 *
	 * @param version must not be {@literal null}.
	 * @return
	 */
	public URI getProjectMetadataResource(MaintainedVersion version) {
		return getProjectReleaseResource(version.getProject(), version.getVersion().toString());
	}
}
