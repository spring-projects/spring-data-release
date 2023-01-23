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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.minidev.json.JSONArray;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.utils.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import com.jayway.jsonpath.JsonPath;

/**
 * Sagan client to interact with the Sagan instance defined through {@link SaganProperties}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class DefaultProjectClient implements ProjectService {

	RestOperations operations;
	ProjectServiceResources configuration;
	Logger logger;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.sagan.SaganClient#getProjectMetadata(org.springframework.data.release.sagan.MaintainedVersion)
	 */
	@Override
	public String getProjectMetadata(MaintainedVersion version) {

		URI resource = configuration.getProjectMetadataResource(version);

		logger.log(version.getProject(), "Getting project metadata for version %s from %s…", version.getVersion(),
				resource);

		return operations.getForObject(resource, String.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.sagan.SaganClient#getProjectMetadata(org.springframework.data.release.model.Project)
	 */
	@Override
	public String getProjectMetadata(Project project) {

		URI resource = configuration.getProjectReleasesResource(project);

		logger.log(project, "Getting listed project versions from %s…", resource);

		return operations.getForObject(resource, String.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.sagan.SaganClient#updateProjectMetadata(org.springframework.data.release.model.Project, java.util.List)
	 */
	@Override
	public void updateProjectMetadata(Project project, MaintainedVersions versions) {

		URI resource = configuration.getProjectReleasesResource(project);

		String versionsString = versions.stream()//
				.map(MaintainedVersion::getVersion)//
				.map(Object::toString) //
				.collect(Collectors.joining(", "));
		List<String> versionsToRetain = getVersionsToWrite(versions).map(ProjectMetadata::getVersion)
				.collect(Collectors.toList());
		List<String> versionsInSagan = new ArrayList<>();

		// Delete all existing versions first

		boolean requiresDelete = requiresDeleteVersions(project, versionsToRetain, versionsInSagan);
		boolean requiresWrite = requiresWriteVersions(versions, versionsInSagan);

		if (requiresDelete || requiresWrite) {
			logger.log(project, "Updating project versions at %s…", resource);
		}

		if (requiresDelete) {

			logger.log(project, "Deleting outdated project versions…", versionsString);
			deleteExistingVersions(project, versionsToRetain);
		}

		if (requiresWrite) {

			logger.log(project, "Writing project versions %s.", versionsString);
			createVersions(project, versions, resource, versionsInSagan);
		}

		logger.log(project, "Project versions up to date: %s", versionsString);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.sagan.SaganClient#verifyAuthentication()
	 */
	@Override
	public void verifyAuthentication() {

		URI resource = configuration.getProjectReleasesResource(Projects.BUILD);

		logger.log("Sagan", "Verifying Sagan Authentication…");

		ResponseEntity<String> entity = operations.getForEntity(resource, String.class);

		if (!entity.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("Cannot access Jira user profile");
		}

		logger.log("Sagan", "Authentication verified!");
	}

	private void createVersions(Project project, MaintainedVersions versions, URI resource,
			List<String> versionsInSagan) {

		getVersionsToWrite(versions) //
				.filter(version -> !versionsInSagan.contains(version.getVersion())) //
				.peek(metadata -> logger.log(project, "Creating project version %s…", metadata.getVersion())) //
				.forEach(payload -> operations.postForObject(resource, payload, String.class));
	}

	private static boolean requiresWriteVersions(MaintainedVersions versions, List<String> versionsInSagan) {
		return getVersionsToWrite(versions) //
				.anyMatch(version -> !versionsInSagan.contains(version.getVersion()));
	}

	private static Stream<ProjectMetadata> getVersionsToWrite(MaintainedVersions versions) {
		return versions.stream() //
				.map(it -> new ProjectMetadata(it, versions));
	}

	private boolean requiresDeleteVersions(Project project, List<String> versionsToRetain, List<String> versionsInSagan) {
		return getVersionsToDelete(project) //
				.peek(versionsInSagan::add) //
				.anyMatch(version -> !versionsToRetain.contains(version));
	}

	private void deleteExistingVersions(Project project, List<String> versionsToRetain) {

		getVersionsToDelete(project) //
				.filter(version -> !versionsToRetain.contains(version)) //
				.map(version -> configuration.getProjectReleaseResource(project, version))//
				.peek(uri -> logger.log(project, "Deleting existing project version at %s…", uri)) //
				.forEach(operations::delete);
	}

	private Stream<String> getVersionsToDelete(Project project) {
		return Arrays.stream(JsonPath.compile("$..version").<JSONArray> read(getProjectMetadata(project)).toArray())//
				.map(Object::toString);
	}
}
