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

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.SupportStatus;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

/**
 * Operations for Projects Service interaction.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Component
@CliComponent
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class ProjectServiceCommands extends TimedCommand {

	ProjectServiceOperations projects;
	GitOperations git;
	ExecutorService executor;
	Workspace workspace;

	@CliCommand("projects update")
	public void updateProjectInformation(@CliOption(key = "", mandatory = false) String trainNames,
			@CliOption(key = "update", mandatory = false) Boolean update) {

		File oss = new File(workspace.getWorkingDirectory(), "oss");
		File commercial = new File(workspace.getWorkingDirectory(), "commercial");
		List<Train> trains = ReleaseTrains.getTrains(trainNames, 3);

		if ((update != null && update) || (!commercial.exists() || !oss.exists())) {

			// avoid race condition in directory creation
			oss.mkdirs();
			commercial.mkdirs();

			for (Train train : trains) {
				ExecutionUtils.run(executor, train, sp -> git.update(SupportedProject.of(sp.getProject(), SupportStatus.OSS)));
			}
		}

		projects.updateProjectMetadata(trains);
	}
}
