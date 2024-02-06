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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.util.Streamable;
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

	@CliCommand("sagan update")
	public void saganUpdateProjectInformation(@CliOption(key = "", mandatory = true) String trainNames) {
		updateProjectInformation(trainNames);
	}

	@CliCommand("projects update")
	public void updateProjectInformation(@CliOption(key = "", mandatory = true) String trainNames) {

		List<Train> trains = Stream.of(trainNames.split(","))//
				.map(ReleaseTrains::getTrainByName)
				.collect(Collectors.toList());

		ExecutionUtils.run(executor, Streamable.of(trains), git::update);

		projects.updateProjectMetadata(trains);
	}
}
