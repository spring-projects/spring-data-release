/*
 * Copyright 2017-2022 the original author or authors.
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
package org.springframework.data.release.projectservice;

import org.junit.jupiter.api.Test;

import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Tests for serialization of {@link ProjectMetadata}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
class ProjectMetadataSerializationTests {

	@Test
	void serializesMaintainedVersionsIntoProjectMetadata() throws Exception {

		ObjectWriter mapper = new ObjectMapper().writerWithDefaultPrettyPrinter();

		MaintainedVersion kay = MaintainedVersion.of(Projects.COMMONS, ArtifactVersion.of("2.0.0.RC1"), ReleaseTrains.KAY,
				null, null);
		MaintainedVersion ingalls = MaintainedVersion.of(Projects.COMMONS, ArtifactVersion.of("1.13.5.RELEASE"),
				ReleaseTrains.INGALLS, null, null);
		MaintainedVersion ingallsSnapshot = ingalls.nextDevelopmentVersion();
		MaintainedVersion hopper = MaintainedVersion.of(Projects.COMMONS, ArtifactVersion.of("1.12.8.RELEASE"),
				ReleaseTrains.HOPPER, null, null);

		MaintainedVersions versions = MaintainedVersions.of(kay, ingalls, hopper);

		/*System.out.println(mapper.writeValueAsString(new ProjectMetadata(kay, SupportStatus.OSS, versions)));
		System.out.println(mapper.writeValueAsString(new ProjectMetadata(ingallsSnapshot, SupportStatus.OSS, versions)));
		System.out.println(mapper.writeValueAsString(new ProjectMetadata(ingalls, SupportStatus.OSS, versions)));
		System.out.println(mapper.writeValueAsString(new ProjectMetadata(hopper, SupportStatus.OSS, versions)));
		 */
	}
}
