/*
 * Copyright 2015-2022 the original author or authors.
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
package org.springframework.data.release.announcement;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;

/**
 * Commands to create markup to be used in announcing blog posts.
 * 
 * @author Oliver Gierke
 */
@CliComponent
@RequiredArgsConstructor
class AnnouncementCommands extends TimedCommand {

	private final @NonNull AnnouncementOperations operations;

	@CliCommand("announcement")
	public void announce(@CliOption(key = "", mandatory = true) TrainIteration iteration) throws Exception {
		System.out.println(operations.getProjectBulletpoints(iteration));
	}
}
