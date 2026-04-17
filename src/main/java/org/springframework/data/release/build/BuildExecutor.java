/*
 * Copyright 2019-2022 the original author or authors.
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
package org.springframework.data.release.build;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

import org.springframework.data.release.infra.InfrastructureOperations;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.ProjectAware;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Build executor service.
 *
 * @author Mark Paluch
 */
@Component
@RequiredArgsConstructor
class BuildExecutor {

	private final BuildSystem buildSystem;
	private final ExecutorService executor;
	private final Workspace workspace;
	private final ObjectMapper objectMapper;

	/**
	 * Selects the build system for each module contained in the given iteration and executes the given function for it
	 * considering pre-requites, honoring the order.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param function must not be {@literal null}.
	 * @return
	 */
	public <T, M extends ProjectAware> BuildResults.Summary<T> doWithBuildSystemOrdered(Streamable<M> iteration,
			BiFunction<BuildSystem, M, T> function) {
		return doWithBuildSystem(iteration, function, true);
	}

	/**
	 * Selects the build system for each module contained in the given iteration and executes the given function for it
	 * considering pre-requites, without considering the execution order.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param function must not be {@literal null}.
	 * @return
	 */
	public <T, M extends ProjectAware> BuildResults.Summary<T> doWithBuildSystemAnyOrder(Streamable<M> iteration,
			BiFunction<BuildSystem, M, T> function) {
		return doWithBuildSystem(iteration, function, false);
	}

	private <T, M extends ProjectAware> BuildResults.Summary<T> doWithBuildSystem(Streamable<M> iteration,
			BiFunction<BuildSystem, M, T> function, boolean considerDependencyOrder) {

		BuildResults<T> buildResults = new BuildResults<>();

		// Add here projects that should be skipped because of a partial deployment to e.g. Sonatype.
		Set<Project> skip = new HashSet<>(Arrays.asList());
		skip.forEach(buildResults::markSkipped);

		List<M> modules = iteration.stream().toList();

		for (int i = 0; i < modules.size(); i++) {

			M moduleIteration = modules.get(i);

			if (skip.contains(moduleIteration.getProject()) || buildResults.hasResult(moduleIteration)) {
				continue;
			}

			if (considerDependencyOrder) {

				// TODO: Build was running while Commons tried to advance
				boolean potentiallyAdvanceModule = false; // buildResults.hasPendingDependencyBuild(moduleIteration);

				// trigger build for dependencies if this
				if (potentiallyAdvanceModule) {

					for (int j = i + 1; j < modules.size(); j++) {

						M modulePeek = modules.get(i);

						if (!buildResults.hasResult(moduleIteration) && buildResults.hasPendingDependencyBuild(modulePeek)) {
							CompletableFuture<T> run = run(moduleIteration, function);
							buildResults.putResult(moduleIteration, run);
						}
					}
				}

				for (Project dependency : moduleIteration.getProject().getDependencies()) {

					CompletableFuture<T> futureResult = buildResults.getFuture(dependency);

					if (futureResult == null) {

						if (iteration.stream().map(ProjectAware::getProject).noneMatch(project -> project.equals(dependency))) {
							throw new IllegalStateException(moduleIteration.getSupportedProject().getName() + " requires "
									+ dependency.getName() + " which is not part of the Iteration. Please fix Projects/Iterations setup");
						}

						throw new IllegalStateException("No future result for " + dependency.getName() + ", required by "
								+ moduleIteration.getSupportedProject().getName());
					}

					futureResult.join();
				}
			}

			CompletableFuture<T> result = buildResults.getFuture(moduleIteration.getProject());

			if (result == null) {
				result = run(moduleIteration, function);
			}
			buildResults.putResult(moduleIteration, result);
		}

		return iteration.stream()//
				.map(module -> {

					CompletableFuture<T> future = buildResults.getFuture(module);

					try {
						return new BuildResults.ExecutionResult<T>(module.getProject(), future.get());
					}

				catch (InterruptedException | ExecutionException e) {
						return new BuildResults.ExecutionResult<T>(module.getProject(), e.getCause());
					}

				}) //
				.collect(BuildResults.toSummaryCollector());
	}


	private <T, M extends ProjectAware> CompletableFuture<T> run(M module, BiFunction<BuildSystem, M, T> function) {

		Assert.notNull(module, "Module must not be null!");

		CompletableFuture<T> result = new CompletableFuture<>();

		BuildSystem buildSystem = this.buildSystem
				.withJavaVersion(detectJavaVersion(module.getSupportedProject()));

		Runnable runnable = () -> {

			try {

				result.complete(function.apply(buildSystem, module));
			} catch (Exception e) {
				result.completeExceptionally(e);
			}
		};

		executor.execute(runnable);

		return result;
	}

	@SneakyThrows
	public JavaVersion detectJavaVersion(SupportedProject project) {

		File configJson = findConfigJson(project);

		if (configJson != null) {

			Map<String, Map<String, String>> map = objectMapper.readValue(configJson, Map.class);
			String java = map.get("java").get("main");
			return JavaVersion.of(java);
		}

		File ciProperties = workspace.getFile(InfrastructureOperations.CI_PROPERTIES, project);

		if (!ciProperties.exists()) {
			throw new IllegalStateException(String.format("Cannot find %s or %s for project %s",
					InfrastructureOperations.CONFIG_JSON, InfrastructureOperations.CI_PROPERTIES, project));
		}

		Properties properties = new Properties();

		try (FileInputStream fis = new FileInputStream(ciProperties)) {
			properties.load(fis);
		}

		return JavaVersion.fromDockerTag(properties.getProperty("java.main.tag"));
	}

	private File findConfigJson(SupportedProject project) {

		List<String> configJsonLocations = List.of(InfrastructureOperations.CONFIG_JSON,
				".github/" + InfrastructureOperations.CONFIG_JSON, "ci/" + InfrastructureOperations.CONFIG_JSON,
				"actions/env-config/" + InfrastructureOperations.CONFIG_JSON);

		SupportedProject build = SupportedProject.of(Projects.BUILD, project.getStatus());
		List<SupportedProject> projectCandidates = List.of(project, build);
		for (SupportedProject projectCandidate : projectCandidates) {
			for (String location : configJsonLocations) {
				File file = workspace.getFile(location, projectCandidate);
				if (file.isFile()) {
					return file;
				}
			}
		}

		return null;
	}

}
