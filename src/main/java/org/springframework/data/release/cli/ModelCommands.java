/*
 * Copyright 2014-2022 the original author or authors.
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

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.Train;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;

/**
 * @author Oliver Gierke
 */
@CliComponent
class ModelCommands extends TimedCommand {

	@CliCommand(value = "trains", help = "Displays all release trains or contents of them if a name is provided")
	public String train(@CliOption(key = { "", "train" }) Train train) {

		if (train != null) {
			return format(train);
		}

		StringBuilder builder = new StringBuilder();
		ReleaseTrains.TRAINS.stream().map(ModelCommands::format)
				.forEach(it -> builder.append("* ").append(it).append(System.lineSeparator()));

		return builder.toString();
	}

	private static String format(Train train) {
		return "%s (%s, %s)".formatted(train.getName(), train.getCalver(), train.getSupportStatus());
	}
}
