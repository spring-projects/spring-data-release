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
package org.springframework.data.release.build;

import static org.springframework.data.release.build.CommandLine.Argument.*;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;

import org.springframework.data.release.io.JavaRuntimes;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.model.Named;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.utils.Logger;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Slf4j
public class MavenRuntime extends MavenRuntimeSupport implements MavenEnvironment {

	private final Workspace workspace;
	private final Logger logger;
	private final MavenProperties properties;

	/**
	 * Creates a new {@link MavenRuntime} for the given {@link Workspace} and Maven home.
	 *
	 * @param workspace must not be {@literal null}.
	 * @param logger must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	public MavenRuntime(Workspace workspace, Logger logger, MavenRuntimes.MavenInstallation mavenInstallation,
			MavenProperties properties) {
		this(workspace, logger, mavenInstallation.getHome(), properties, JavaVersion.VERSION_1_8);
	}

	private MavenRuntime(Workspace workspace, Logger logger, File mavenHome, MavenProperties properties,
			JavaVersion requiredJavaVersion) {

		super(mavenHome, properties.getLocalRepository(),
				JavaRuntimes.Selector.from(requiredJavaVersion).notGraalVM().getRequiredJdkInstallation());
		this.workspace = workspace;
		this.logger = logger;
		this.properties = properties;
		logger.log("Maven", "Using " + getJdk() + " as default Java Runtime");
	}

	@Override
	public MavenRuntime withJavaVersion(JavaVersion javaVersion) {
		return new MavenRuntime(workspace, logger, getMavenHome(), properties, javaVersion);
	}

	@Override
	public MavenInvocationResult execute(SupportedProject project, CommandLine arguments) {

		logger.log(project, "📦 Executing mvn %s", arguments.toString());

		try (MavenLogger mavenLogger = getLogger(project, arguments.getGoals())) {

			Invoker invoker = new DefaultInvoker();
			invoker.setMavenHome(getMavenHome());
			invoker.setOutputHandler(mavenLogger::info);
			invoker.setErrorHandler(mavenLogger::warn);

			InvocationResult result = doWithMaven(invoker, mvn -> {

				mvn.setBaseDirectory(workspace.getProjectDirectory(project));
				mavenLogger.info(String.format("Java Home: %s", getJavaHome()));
				mavenLogger.info(String.format("Executing: mvn %s", arguments));

				CommandLine disabledGradleBuildCache = arguments.and(arg("gradle.cache.local.enabled=false"))
						.and(arg("gradle.cache.remote.enabled=false")).and(arg("develocity.cache.local.enabled=false"))
						.and(arg("develocity.cache.remote.enabled=false"));

				mvn.setGoals(disabledGradleBuildCache.toCommandLine(it -> properties.getFullyQualifiedPlugin(it.getGoal())));
			});

			if (result.getExitCode() != 0) {
				logger.warn(project, "🙈 Failed execution mvn %s", arguments.toString());

				throw new IllegalStateException("🙈 Failed execution mvn " + arguments, result.getExecutionException());
			}
			logger.log(project, "🆗 Successful execution mvn %s", arguments.toString());

			MavenInvocationResult invocationResult = new MavenInvocationResult();
			invocationResult.getLog().addAll(mavenLogger.getLines());

			return invocationResult;
		} catch (Exception e) {
			if (e instanceof RuntimeException exception) {
				throw exception;
			}
			throw new RuntimeException(e);
		}
	}

	@Override
	MavenLogger getLogger(Named project, List<CommandLine.Goal> goals) {

		if (this.properties.isConsoleLogger()) {
			return new SlfLogger(log, project);
		}

		return new FileLogger(log, project, this.workspace.getLogsDirectory(), goals);
	}

}
