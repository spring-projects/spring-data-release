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
package org.springframework.data.release.documentation;

import static org.springframework.data.release.model.Projects.*;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.build.BuildOperations;
import org.springframework.data.release.cli.StaticResources;
import org.springframework.data.release.documentation.DocumentationOperations.ReportFlags;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.release.utils.Logger;
import org.springframework.data.util.Streamable;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;

/**
 * @author Christoph Strobl
 */
@CliComponent
@RequiredArgsConstructor
public class DocumentationCommands extends TimedCommand {

	private final @NonNull DocumentationOperations operations;
	private final @NonNull ExecutorService executorService;
	private final @NonNull BuildOperations buildOperations;
	private final @NonNull Workspace workspace;
	private final @NonNull Logger logger;

	@CliCommand("docs check-links")
	public void checkLinks(@CliOption(key = "", mandatory = true) TrainIteration iteration, @CliOption(key = "project", mandatory = false) Project project, @CliOption(key = "local", mandatory = false, unspecifiedDefaultValue = "false") boolean preview, @CliOption(key = "report", mandatory = false) String options) {

		if (project != null) {
			checkLinks(iteration.getModule(project), preview, options);
			return;
		}

		ExecutionUtils.run(executorService, Streamable.of(iteration.getModulesExcept(BUILD, BOM, COMMONS)), module -> {
			checkLinks(module, preview, options);
		});
	}

	public void checkLinks(ModuleIteration module, boolean preview, String options) {

		ReportFlags[] flags = new ReportFlags[]{ReportFlags.ALL};
		if (options != null) {
			flags = Arrays.stream(options.split(",")).map(ReportFlags::valueOf).toArray(ReportFlags[]::new);
		}

		if (preview) {
			buildOperations.buildDocumentation(module);
			File projectDirectory = workspace.getProjectDirectory(module.getProject());
			if (!projectDirectory.exists()) {
				logger.warn(module, "Unable to locate project directory");
				return;
			}
			File source = new File(projectDirectory, "target/site/reference/html/index.html");
			if (!source.exists()) {
				logger.warn(module, "Unable to locate reference documentation html %", source);
				return;
			}

			String result = operations.checkDocumentation(source.getPath()).prettyPrint(flags);
			logger.log(module, "%s Documentation Link Statistic:\r\n%s", module, result);
		} else {

			String result = operations.checkDocumentation(new StaticResources(module).getDocumentationUrl()).prettyPrint(flags);
			logger.log(module, "%s Documentation Link Statistic:\r\n%s", module, result);
		}
	}
}

