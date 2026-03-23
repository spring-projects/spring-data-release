/*
 * Copyright 2026-present the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.ProjectAware;
import org.springframework.data.release.utils.ListWrapperCollector;
import org.springframework.lang.Nullable;

/**
 * Encapsulates build execution results for projects, tracking the asynchronous completion of build operations.
 * <p>
 * This value object maintains the mapping between projects and their corresponding {@link CompletableFuture} instances,
 * providing explicit operations for result management and dependency tracking.
 *
 * @param <T> the type of the build result
 * @author Mark Paluch
 */
public class BuildResults<T> {

	private final Map<Project, CompletableFuture<T>> results = new ConcurrentHashMap<>();

	/**
	 * Returns a new collector to toSummaryCollector {@link ExecutionResult} as {@link Summary} using the {@link Stream}
	 * API.
	 *
	 * @return
	 */
	public static <T> Collector<ExecutionResult<T>, ?, Summary<T>> toSummaryCollector() {
		return ListWrapperCollector.collectInto(Summary::new);
	}

	/**
	 * Mark the given project as skipped by associating it with an already completed future.
	 *
	 * @param project the project to mark as skipped
	 */
	public void markSkipped(Project project) {
		this.results.put(project, CompletableFuture.completedFuture(null));
	}

	/**
	 * Check whether a result is already registered for the given project.
	 *
	 * @param aware the project to check.
	 * @return {@code true} if a result exists, {@code false} otherwise.
	 */
	public boolean hasResult(ProjectAware aware) {
		return hasResult(aware.getProject());
	}

	/**
	 * Check whether a result is already registered for the given project.
	 *
	 * @param project the project to check.
	 * @return {@code true} if a result exists, {@code false} otherwise.
	 */
	public boolean hasResult(Project project) {
		return this.results.containsKey(project);
	}

	/**
	 * Retrieve the {@link CompletableFuture} associated with the given project.
	 *
	 * @param aware the project to retrieve the future for.
	 * @return the associated future, or {@code null} if none is registered
	 */
	@Nullable
	public CompletableFuture<T> getFuture(ProjectAware aware) {
		return getFuture(aware.getProject());
	}

	/**
	 * Retrieve the {@link CompletableFuture} associated with the given project.
	 *
	 * @param project the project to retrieve the future for.
	 * @return the associated future, or {@code null} if none is registered
	 */
	@Nullable
	public CompletableFuture<T> getFuture(Project project) {
		return this.results.get(project);
	}

	/**
	 * Register a build result for the given project.
	 *
	 * @param aware the project to register the result for.
	 * @param future the future representing the asynchronous build result
	 */
	public void putResult(ProjectAware aware, CompletableFuture<T> future) {
		putResult(aware.getProject(), future);
	}

	/**
	 * Register a build result for the given project.
	 *
	 * @param project the project to register the result for
	 * @param future the future representing the asynchronous build result
	 */
	public void putResult(Project project, CompletableFuture<T> future) {
		this.results.put(project, future);
	}

	/**
	 * Check whether any dependency of the given module has a pending (not yet completed) build.
	 *
	 * @param moduleIteration the module whose dependencies should be checked
	 * @return {@code true} if any dependency build is pending, {@code false} if all are completed or absent
	 */
	public boolean hasPendingDependencyBuild(ProjectAware moduleIteration) {

		for (Project dependency : moduleIteration.getProject().getDependencies()) {

			CompletableFuture<T> futureResult = this.results.get(dependency);

			if (futureResult == null || !futureResult.isDone()) {
				return true;
			}
		}

		return false;
	}

	public static class ExecutionResult<T> {

		private final Project project;
		private final T result;
		private final Throwable failure;

		public ExecutionResult(Project project, Throwable failure) {
			this.project = project;
			this.result = null;
			this.failure = failure;
		}

		public ExecutionResult(Project project, T result) {
			this.project = project;
			this.result = result;
			this.failure = null;
		}

		public T getResult() {
			return result;
		}

		@Override
		public String toString() {
			return String.format("%-14s - %s", project.getName(),
					isSuccessful() ? "🆗 Successful" : "🧨 Error: " + failure.getMessage());
		}

		public boolean isSuccessful() {
			return this.failure == null;
		}
	}

	public record Summary<T>(List<ExecutionResult<T>> executions) {

		public Summary(List<ExecutionResult<T>> executions) {
			this.executions = executions;

			if (!isSuccessful()) {
				throw new BuildFailed(this);
			}
		}

		public boolean isSuccessful() {
			return this.executions.stream().allMatch(ExecutionResult::isSuccessful);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Execution summary");
			builder.append(System.lineSeparator());
			builder.append(executions.stream().map(it -> "\t" + it).collect(Collectors.joining(System.lineSeparator())));

			return builder.toString();
		}
	}

	static class BuildFailed extends RuntimeException {

		public BuildFailed(Summary<?> summary) {
			super(summary.toString());
		}
	}
}
