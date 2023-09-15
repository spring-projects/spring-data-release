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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.release.io.JavaRuntimes;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.utils.Logger;
import org.springframework.lang.Nullable;
import org.springframework.shell.support.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Slf4j
@Component
public class MavenRuntime {

	private static final Pattern versionPattern = Pattern.compile("Apache Maven ((\\d\\.?)+) \\(.*\\)");
	private final Workspace workspace;
	private final Logger logger;
	private final MavenProperties properties;
	private final JavaRuntimes.JdkInstallation jdk;

	/**
	 * Creates a new {@link MavenRuntime} for the given {@link Workspace} and Maven home.
	 *
	 * @param workspace must not be {@literal null}.
	 * @param logger must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	@Autowired
	public MavenRuntime(Workspace workspace, Logger logger, MavenProperties properties) {
		this(workspace, logger, properties, JavaVersion.VERSION_1_8);
	}

	private MavenRuntime(Workspace workspace, Logger logger, MavenProperties properties,
			JavaVersion requiredJavaVersion) {

		this.workspace = workspace;
		this.logger = logger;
		this.properties = properties;
		this.jdk = JavaRuntimes.Selector.from(requiredJavaVersion).notGraalVM().getRequiredJdkInstallation();
		logger.log("Maven", "Using" + jdk + " as default Java Runtime");
	}

	public MavenRuntime withJavaVersion(JavaVersion javaVersion) {
		return new MavenRuntime(workspace, logger, properties, javaVersion);
	}

	@SneakyThrows
	public String getVersion() throws IllegalStateException {

		String version = detectBuildPropertiesVersion();

		return version != null ? version : runVersionCommand();
	}

	@Nullable
	@SneakyThrows
	private String detectBuildPropertiesVersion() {

		File libs = new File(properties.getMavenHome(), "lib");
		File[] files = libs.listFiles((FileFilter) new PrefixFileFilter("maven-core-"));

		if (files == null || files.length != 1) {
			return null;
		}

		try (ZipFile zipFile = new ZipFile(files[0])) {

			ZipEntry entry = zipFile.getEntry("org/apache/maven/messages/build.properties");

			if (entry == null) {
				return null;
			}

			Properties properties = new Properties();
			try (InputStream inputStream = zipFile.getInputStream(entry)) {
				properties.load(inputStream);
			}

			return properties.getProperty("version");
		}
	}

	private String runVersionCommand() throws MavenInvocationException {

		StringBuilder builder = new StringBuilder();
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(properties.getMavenHome());
		invoker.setErrorHandler(builder::append);
		invoker.setOutputHandler(builder::append);

		doWithMaven(invoker, mvn -> {
			mvn.setShowVersion(true);
			mvn.setGoals(Collections.emptyList());
		});

		Matcher matcher = versionPattern.matcher(builder);
		boolean foundVersion = matcher.find();

		if (!foundVersion) {
			throw new IllegalStateException("Cannot determine Maven Version: " + builder);
		}

		return matcher.group(1);
	}

	public MavenInvocationResult execute(Project project, CommandLine arguments) {

		logger.log(project, "📦 Executing mvn %s", arguments.toString());

		try (MavenLogger mavenLogger = getLogger(project, arguments.getGoals())) {

			Invoker invoker = new DefaultInvoker();
			invoker.setMavenHome(properties.getMavenHome());
			invoker.setOutputHandler(mavenLogger::info);
			invoker.setErrorHandler(mavenLogger::warn);

			InvocationResult result = doWithMaven(invoker, mvn -> {

				mvn.setBaseDirectory(workspace.getProjectDirectory(project));
				mavenLogger.info(String.format("Java Home: %s", jdk));
				mavenLogger.info(String.format("Executing: mvn %s", arguments));

				mvn.setGoals(arguments.toCommandLine(it -> properties.getFullyQualifiedPlugin(it.getGoal())));
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
			if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			}
			throw new RuntimeException(e);
		}
	}

	private InvocationResult doWithMaven(Invoker invoker, Consumer<InvocationRequest> mvn)
			throws MavenInvocationException {

		File localRepository = properties.getLocalRepository();

		if (localRepository != null) {
			invoker.setLocalRepositoryDirectory(localRepository);
		}

		File javaHome = getJavaHome();
		InvocationRequest request = new DefaultInvocationRequest();
		request.setJavaHome(javaHome);
		request.setShellEnvironmentInherited(true);
		request.setBatchMode(true);

		mvn.accept(request);

		return invoker.execute(request);
	}

	private File getJavaHome() {
		return jdk.getHome().getAbsoluteFile();
	}

	private MavenLogger getLogger(Project project, List<CommandLine.Goal> goals) {

		if (this.properties.isConsoleLogger()) {
			return new SlfLogger(log, project);
		}

		return new FileLogger(log, project, this.workspace.getLogsDirectory(), goals);
	}

	public static class MavenInvocationResult {

		private final List<String> log = new ArrayList<>();

		public List<String> getLog() {
			return log;
		}
	}

	/**
	 * Maven Logging Forwarder.
	 */
	interface MavenLogger extends Closeable {

		void info(String message);

		void warn(String message);

		List<String> getLines();
	}

	@RequiredArgsConstructor
	static class SlfLogger implements MavenLogger {

		private final org.slf4j.Logger logger;
		private final String logPrefix;
		private final List<String> contents;

		SlfLogger(org.slf4j.Logger logger, Project project) {
			this.logger = logger;
			this.logPrefix = StringUtils.padRight(project.getName(), 10);
			this.contents = new ArrayList<>();
		}

		@Override
		public void info(String message) {
			String msg = logPrefix + ": " + message;
			contents.add(msg);
			logger.info(msg);
		}

		@Override
		public void warn(String message) {
			String msg = logPrefix + ": " + message;
			contents.add(msg);
			logger.warn(msg);
		}

		@Override
		public void close() throws IOException {
			// no-op
		}

		@Override
		public List<String> getLines() {
			return contents;
		}
	}

	static class FileLogger implements MavenLogger {

		private final PrintWriter printWriter;
		private final FileOutputStream outputStream;
		private final List<String> contents = new ArrayList<>();

		FileLogger(org.slf4j.Logger logger, Project project, File logsDirectory, List<CommandLine.Goal> goals) {

			if (!logsDirectory.exists()) {
				logsDirectory.mkdirs();
			}

			String goalNames = goals.stream().map(CommandLine.Goal::getGoal).collect(Collectors.joining("-"));

			String filename = String.format("mvn-%s-%s.log", project.getName(), goalNames).replace(':', '.');

			try {
				File file = new File(logsDirectory, filename);
				logger.info("Routing Maven output to " + file.getCanonicalPath());
				outputStream = new FileOutputStream(file, true);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			printWriter = new PrintWriter(outputStream, true);
		}

		@Override
		public void info(String message) {
			printWriter.println(message);
			contents.add(message);
		}

		@Override
		public void warn(String message) {
			printWriter.println(message);
			contents.add(message);
		}

		@Override
		public void close() throws IOException {
			printWriter.close();
			outputStream.close();
		}

		@Override
		public List<String> getLines() {
			return contents;
		}
	}

}
