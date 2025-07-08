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
package org.springframework.data.release.utils;

import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Named;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.shell.support.logging.HandlerUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Component
public class Logger {

	private static final String PREFIX_TEMPLATE = "%-14s > %s";

	private final java.util.logging.Logger LOGGER = HandlerUtils.getLogger(getClass());

	public void log(ModuleIteration module, Object template, Object... args) {
		log(module.getSupportedProject(), template, args);
	}

	public void log(SupportedProject project, Object template, Object... args) {
		log(project.getProject(), template, args);
	}

	public void log(Project project, Object template, Object... args) {
		log(project.getName(), template, args);
	}

	public void log(TrainIteration iteration, Object template, Object... args) {
		log(iteration.toString(), template, args);
	}

	public void log(Train train, Object template, Object... args) {
		log(train.getName(), template, args);
	}

	public void log(String context, Object template, Object... args) {
		LOGGER.info(PREFIX_TEMPLATE.formatted(context,
				ObjectUtils.isEmpty(args) ? template.toString() : template.toString().formatted(args)));
	}

	public void warn(ModuleIteration module, Object template, Object... args) {
		warn(module.getSupportedProject(), template, args);
	}

	public void warn(Named project, Object template, Object... args) {
		warn(project.getName(), template, args);
	}

	public void warn(TrainIteration iteration, Object template, Object... args) {
		warn(iteration.toString(), template, args);
	}

	public void warn(Train train, Object template, Object... args) {
		warn(train.getName(), template, args);
	}

	public void warn(String context, Object template, Object... args) {
		LOGGER.warning(PREFIX_TEMPLATE.formatted(context,
				ObjectUtils.isEmpty(args) ? template.toString() : template.toString().formatted(args)));
	}
}
