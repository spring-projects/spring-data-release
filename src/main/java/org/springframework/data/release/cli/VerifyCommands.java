/*
 * Copyright 2021-2022 the original author or authors.
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
package org.springframework.data.release.cli;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.build.BuildOperations;
import org.springframework.data.release.deployment.DeploymentOperations;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.issues.github.GitHub;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.projectservice.ProjectService;
import org.springframework.data.release.utils.Logger;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.util.ObjectUtils;

/**
 * Commands to verify a correct Release Tools Setup.
 *
 * @author Mark Paluch
 */
@CliComponent
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class VerifyCommands extends TimedCommand {

	@NonNull GitOperations git;
	@NonNull GitHub github;
	@NonNull DeploymentOperations deployment;
	@NonNull BuildOperations build;
	@NonNull ProjectService projectService;
	@NonNull Logger logger;

	@CliCommand("verify")
	public void verifyReleaseTools(
			@CliOption(key = "", mandatory = false) String mode,
			@CliOption(key = "train", mandatory = true) Train train) {

		if ("local".equals(mode)) {

			// Git checkout build
			git.verify(train);

			// Maven interaction
			build.verify(train);

			// GitHub verification
			github.verifyAuthentication(train);

			// Projects Service Verification
			projectService.verifyAuthentication();

			logger.log("Verify", "All local settings are verified. You can ship a release now.");
			return;
		}

		if (ObjectUtils.isEmpty(mode) || "git".equals(mode)) {
			// Git checkout build
			git.verify(train);
		}

		if (ObjectUtils.isEmpty(mode) || "build".equals(mode)) {
			// Maven interaction
			build.verify(train);
			build.verifyStagingAuthentication(train);
		}

		if (ObjectUtils.isEmpty(mode) || "deployment".equals(mode)) {
			// Artifactory verification
			deployment.verifyAuthentication(train);
		}

		if (ObjectUtils.isEmpty(mode) || "github".equals(mode)) {
			// GitHub verification
			github.verifyAuthentication(train);
		}

		if (ObjectUtils.isEmpty(mode) || "projects".equals(mode)) {
			// Projects Service Verification
			projectService.verifyAuthentication();
		}

		logger.log("Verify", "All settings are verified. You can ship a release now.");
	}
}
