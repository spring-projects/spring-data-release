/*
 * Copyright 2016-2022 the original author or authors.
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
package org.springframework.data.release.deployment;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.stream.Stream;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.SupportStatus;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.Logger;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;

/**
 * Commands to interact with Artifactory.
 *
 * @author Oliver Gierke
 */
@CliComponent
@RequiredArgsConstructor
class ArtifactoryCommands extends TimedCommand {

	private final @NonNull DeploymentOperations deployment;
	private final @NonNull ArtifactoryOperations operations;
	private final @NonNull Logger log;

	@CliCommand(value = "artifactory verify", help = "Verifies authentication at Artifactory.")
	public void verify() {

		Stream.of(SupportStatus.OSS, SupportStatus.COMMERCIAL).forEach(deployment::verifyAuthentication);
	}

	@CliCommand(value = "artifactory release create")
	@SneakyThrows
	public void createArtifactoryReleases(@CliOption(key = "", mandatory = true) TrainIteration trainIteration) {

		if (trainIteration.isCommercial()) {

			log.log(trainIteration, "Creating Artifactory Release bundle");

			for (ModuleIteration moduleIteration : trainIteration) {
				operations.createArtifactoryRelease(moduleIteration);
			}

			// aggregator creation requires a bit of time
			// otherwise we will see 16:19:04 "message" : "Release Bundle path not found:
			// spring-release-bundles-v2/TNZ-spring-data-rest-commercial/4.0.15/release-bundle.json.evd"
			Thread.sleep(2000);

			operations.createArtifactoryReleaseAggregator(trainIteration);
		} else {
			log.log(trainIteration, "Skipping Artifactory Release bundle creation, not a commercial release");
		}
	}

	@CliCommand(value = "artifactory release distribute")
	@SneakyThrows
	public void distributeArtifactoryReleases(@CliOption(key = "", mandatory = true) TrainIteration trainIteration) {
		operations.distributeArtifactoryReleaseAggregator(trainIteration);
	}
}
