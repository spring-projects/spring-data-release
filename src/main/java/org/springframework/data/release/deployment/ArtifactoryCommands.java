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

	@CliCommand(value = "artifactory verify", help = "Verifies authentication at Artifactory.")
	public void verify() {

		Stream.of(SupportStatus.OSS, SupportStatus.COMMERCIAL).forEach(deployment::verifyAuthentication);
	}

	@CliCommand(value = "artifactory create releases")
	@SneakyThrows
	public void createArtifactoryReleases(@CliOption(key = "", mandatory = true) TrainIteration trainIteration) {

		for (ModuleIteration moduleIteration : trainIteration) {
			operations.createArtifactoryRelease(moduleIteration);
		}
	}
}
