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

import org.springframework.data.release.io.JavaRuntimes;
import org.springframework.data.release.model.Named;
import org.springframework.lang.Nullable;
import org.springframework.shell.support.util.StringUtils;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Slf4j
public class MavenRuntimeSupport {

	private static final Pattern versionPattern = Pattern.compile("Apache Maven ((\\d\\.?)+) \\(.*\\)");
	private final File mavenHome;
	private final @Nullable File localRepository;
	private final JavaRuntimes.JdkInstallation jdk;

	/**
	 * Creates a new {@link MavenRuntimeSupport}
	 *
	 * @param mavenHome
	 * @param localRepository
	 * @param jdk
	 */
	public MavenRuntimeSupport(File mavenHome, @Nullable File localRepository, JavaRuntimes.JdkInstallation jdk) {
		this.mavenHome = mavenHome;
		this.localRepository = localRepository;
		this.jdk = jdk;
	}

	JavaRuntimes.JdkInstallation getJdk() {
		return jdk;
	}

	File getMavenHome() {
		return mavenHome;
	}

	@SneakyThrows
	public String getVersion() throws IllegalStateException {

		String version = detectBuildPropertiesVersion();

		return version != null ? version : runVersionCommand();
	}

	@Nullable
	@SneakyThrows
	private String detectBuildPropertiesVersion() {

		File libs = new File(mavenHome, "lib");
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
		invoker.setMavenHome(mavenHome);
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

	protected InvocationResult doWithMaven(Invoker invoker, Consumer<InvocationRequest> mvn)
			throws MavenInvocationException {

		if (this.localRepository != null) {
			invoker.setLocalRepositoryDirectory(this.localRepository);
		}

		File javaHome = getJavaHome();
		InvocationRequest request = new DefaultInvocationRequest();
		request.setJavaHome(javaHome);
		request.setShellEnvironmentInherited(true);
		request.setBatchMode(true);

		mvn.accept(request);

		return invoker.execute(request);
	}

	File getJavaHome() {
		return jdk.getHome().getAbsoluteFile();
	}

	MavenLogger getLogger(Named project, List<CommandLine.Goal> goals) {
		return new SlfLogger(log, project);
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

		SlfLogger(org.slf4j.Logger logger, Named project) {
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

		FileLogger(org.slf4j.Logger logger, Named project, File logsDirectory, List<CommandLine.Goal> goals) {

			if (!logsDirectory.exists()) {
				logsDirectory.mkdirs();
			}

			String goalNames = goals.stream().map(CommandLine.Goal::getGoal).collect(Collectors.joining("-"));

			String filename = "mvn-%s-%s.log".formatted(project.getName(), goalNames).replace(':', '.');

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
