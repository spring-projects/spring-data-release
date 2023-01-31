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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.build.BuildOperations;
import org.springframework.data.release.cli.StaticResources;
import org.springframework.data.release.documentation.DocumentationOperations.CheckedLink;
import org.springframework.data.release.documentation.DocumentationOperations.PageStats;
import org.springframework.data.release.documentation.DocumentationOperations.ReportFlags;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.release.utils.Logger;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.table.Table;
import org.springframework.shell.support.table.TableHeader;
import org.springframework.util.StringUtils;

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
	public Table checkLinks(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "project", mandatory = false) Project project,
			@CliOption(key = "local", mandatory = false, unspecifiedDefaultValue = "false") boolean preview,
			@CliOption(key = "report", mandatory = false) String options) {

		if (project != null) {
			return checkLinks(iteration.getModule(project), preview, options);
		}

		Collection<Optional<PageStats>> optionals = ExecutionUtils.runAndReturn(executorService,
				Streamable.of(iteration.getModulesExcept(BUILD, BOM, COMMONS)), module -> {
					Optional<String> path = prepareDocumentationCheck(module, preview);
					return path.map(it -> operations.checkDocumentation(module.getProject(), it));
				});

		return render(optionals.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()),
				options);
	}

	@CliCommand("check-links")
	public Table checkLinks(@CliOption(key = "", mandatory = true) String url,
			@CliOption(key = "report", mandatory = false) String options) {

		PageStats pageStats = operations.checkDocumentation(BOM, url);

		return render(pageStats, options);
	}

	public Optional<String> prepareDocumentationCheck(ModuleIteration module, boolean preview) {

		String path;

		if (preview) {
			buildOperations.buildDocumentation(module);

			File projectDirectory = workspace.getProjectDirectory(module.getProject());

			if (!projectDirectory.exists()) {
				logger.warn(module, "Unable to locate project directory");
				return Optional.empty();
			}

			File source = new File(projectDirectory, "target/site/reference/html/index.html");

			if (!source.exists()) {
				logger.warn(module, "Unable to locate reference documentation html %", source);
				return Optional.empty();
			}

			path = source.getPath();
		} else {
			path = new StaticResources(module).getDocumentationUrl();
		}

		if (!StringUtils.hasText(path)) {
			logger.warn(module, "Empty path for reference documentation.");
			return Optional.empty();
		}

		return Optional.of(path);
	}

	private Table checkLinks(ModuleIteration module, boolean preview, String options) {

		Optional<String> path = prepareDocumentationCheck(module, preview);

		return path.map(s -> {

			PageStats pageStats = operations.checkDocumentation(module.getProject(), s);
			return render(pageStats, options);
		}).orElse(null);
	}

	private Table render(Collection<PageStats> results, String options) {

		return createTable(options, (table, reportFlags) -> {
			for (PageStats pageStats : results) {
				addToTable(pageStats.getProject(), pageStats.filter(reportFlags), table);
			}
		});
	}

	private Table render(PageStats pageStats, String options) {

		return createTable(options,
				(table, reportFlags) -> addToTable(pageStats.getProject(), pageStats.filter(reportFlags), table));
	}

	private Table createTable(String options, BiConsumer<Table, ReportFlags> c) {

		Table table = createResultTable();
		ReportFlags flags = ReportFlags.parse(options);

		c.accept(table, flags);
		return table;
	}

	private void addToTable(Project project, PageStats pageStats, Table table) {

		pageStats.sort(Comparator.<CheckedLink, Integer> comparing(it -> it.getResult().value())
				.thenComparing(CheckedLink::getUrl)).forEach(checkedLink -> {

					Ansi ansi = Ansi.ansi();
					HttpStatus status = checkedLink.getResult();
					if (status.is2xxSuccessful()) {
						ansi.fg(Color.GREEN);
					} else if (status.is4xxClientError())
						ansi.fg(Color.RED);
					else if (status.is3xxRedirection()) {
						ansi.fg(Color.YELLOW);
					}

					String renderedStatus = ansi.a(checkedLink.getResult().value()).fg(Color.DEFAULT).toString();

					table.addRow(project.getName(), status.value() + "", checkedLink.getUrl());
				});
	}

	private static Table createResultTable() {

		Table table = new Table() {
			@Override
			public void calculateColumnWidths() {}
		};
		table.addHeader(1, newHeader("Module", 15));
		table.addHeader(2, newHeader("Status", 15));
		table.addHeader(3, newHeader("URL", 150));
		return table;
	}

	private static TableHeader newHeader(String desc, int width) {
		TableHeader tableHeader = new TableHeader(desc, width);
		tableHeader.setMaxWidth(width);
		return tableHeader;
	}
}
