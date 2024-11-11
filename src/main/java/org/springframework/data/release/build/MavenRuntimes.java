/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.data.release.build;

import lombok.Value;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.data.release.io.JavaRuntimes;
import org.springframework.data.release.model.Version;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Utility to detect a Java runtime version.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public class MavenRuntimes {

	private static final List<MavenDetector> DETECTORS = Arrays.asList(new SDKmanJdkDetector(),
			new MavenWrapperDetector(), new MavenHomeEnvironmentDetector());

	private final List<MavenDetector> detectors;

	public MavenRuntimes(MavenDetector... detectors) {
		this.detectors = new ArrayList<>(DETECTORS);
		this.detectors.addAll(Arrays.asList(detectors));
	}

	public static MavenDetector detector(File mavenHome) {
		return new MavenHomeJdkDetectorSupport(mavenHome);
	}

	/**
	 * Lookup a {@link MavenInstallation} by detecting installed Maven installations and applying the {@link Predicate
	 * filter}. Returns the first matching one {@code null}
	 */
	@Nullable
	public MavenInstallation findMavenInstallation(JavaRuntimes.JdkInstallation jdk,
			Predicate<MavenInstallation> filter) {

		List<MavenInstallation> jdks = getMavenInstallations(jdk);

		return jdks.stream().filter(filter).findFirst().orElse(null);
	}

	/**
	 * Lookup a {@link MavenInstallation} by detecting installed Maven installations and applying the {@link Predicate
	 * filter}. Returns the first matching one or throws {@link NoSuchElementException}.
	 */
	public MavenInstallation getRequiredMaven(JavaRuntimes.JdkInstallation jdk, Predicate<MavenInstallation> filter,
			String runtimeName, Supplier<String> message) {

		List<MavenInstallation> jdks = getMavenInstallations(jdk);

		return jdks.stream().filter(filter).findFirst().orElseThrow(() -> new NoSuchMavenRuntimeException(
				String.format("%s%nAvailable Maven: %s", message.get(), jdks), jdks, runtimeName));
	}

	public List<MavenInstallation> getMavenInstallations(JavaRuntimes.JdkInstallation jdk) {
		return DETECTORS.stream() //
				.filter(MavenDetector::isAvailable) //
				.flatMap(it -> it.detect(jdk).stream()) //
				.sorted() //
				.collect(Collectors.toList());
	}

	static boolean isDirectory(File file) {
		return file.exists() && file.isDirectory();
	}

	/**
	 * Maven detection strategy.
	 */
	public interface MavenDetector {

		/**
		 * @return {@code true} if the detector strategy is available.
		 */
		boolean isAvailable();

		/**
		 * @return a list of Maven installations.
		 */
		List<MavenInstallation> detect(JavaRuntimes.JdkInstallation jdk);

	}

	/**
	 * Selector to determine a {@link MavenInstallation}.
	 */
	public static class Selector {

		private final MavenDetector[] detectors;
		private String notFoundMessage;

		private String mavenRuntimeName;
		private Predicate<MavenInstallation> predicate;

		private Selector(MavenDetector[] detectors) {
			this.detectors = detectors;
		}

		public static Selector builder(MavenDetector... detectors) {
			return new Selector(detectors);
		}

		public Selector version(Version mavenVersion) {

			return and(it -> it.version.equals(mavenVersion)).name("Maven version " + mavenVersion)
					.message("Cannot find required Maven version " + mavenVersion);
		}

		public Selector and(Predicate<MavenInstallation> predicate) {
			this.predicate = this.predicate == null ? predicate : this.predicate.and(predicate);
			return this;
		}

		public Selector message(String notFoundMessage) {
			this.notFoundMessage = notFoundMessage;
			return this;
		}

		public Selector name(String mavenRuntimeName) {
			this.mavenRuntimeName = mavenRuntimeName;
			return this;
		}

		public MavenInstallation getRequiredMavenInstallation(JavaRuntimes.JdkInstallation jdk) {
			return new MavenRuntimes(detectors).getRequiredMaven(jdk, predicate, mavenRuntimeName, () -> notFoundMessage);
		}

	}

	/**
	 * Detector using the SDKman utility storing Java installations in {@code ~/.sdkman/candidates/maven}.
	 */
	static class SDKmanJdkDetector implements MavenDetector {

		private static final File sdkManMavenHome;

		private static final Pattern CANDIDATE = Pattern.compile("(\\d+[\\.\\d+]+)");

		static {

			if (System.getenv().containsKey("SDKMAN_CANDIDATES_DIR")) {
				sdkManMavenHome = new File(System.getenv().get("SDKMAN_CANDIDATES_DIR"), "maven");
			} else if (System.getenv().containsKey("SDKMAN_DIR")) {
				sdkManMavenHome = new File(System.getenv().get("SDKMAN_DIR"), "candidates/maven");
			} else {
				sdkManMavenHome = new File(FileUtils.getUserDirectoryPath(), ".sdkman/candidates/maven");
			}
		}

		@Override
		public boolean isAvailable() {
			return isDirectory(sdkManMavenHome);
		}

		@Override
		public List<MavenInstallation> detect(JavaRuntimes.JdkInstallation jdk) {

			File[] files = sdkManMavenHome.listFiles((FileFilter) new RegexFileFilter(CANDIDATE));

			return Arrays.stream(files).map(it -> {

				Matcher matcher = CANDIDATE.matcher(it.getName());
				if (!matcher.find()) {
					throw new IllegalArgumentException("Cannot determine Maven version number from SDKman candidate name "
							+ it.getName() + ". This should not happen in an ideal world, check the CANDIDATE regex.");
				}

				String candidateVersion = matcher.group(1);
				Version version = Version.parse(candidateVersion);

				return new MavenInstallation(version, it);

			}).collect(Collectors.toList());
		}

	}

	/**
	 * Detector Maven wrapper installations in {@code ~/.m2/wrapper/dists}.
	 */
	static class MavenWrapperDetector implements MavenDetector {

		private static final Pattern CANDIDATE = Pattern.compile("apache-maven-((:?\\d+(:?\\.\\d+)*)(:?_+\\d+)?)-bin");

		private static final String userHome = System.getProperty("user.home");
		private static final File dists = new File(userHome, ".m2/wrapper/dists");

		@Override
		public boolean isAvailable() {
			return isDirectory(dists);
		}

		@Override
		public List<MavenInstallation> detect(JavaRuntimes.JdkInstallation jdk) {

			File[] files = dists.listFiles((FileFilter) new RegexFileFilter(CANDIDATE));

			class WrapperCandidate {

				final File home;
				final File hash;
				final File realHome;
				final Version version;

				public WrapperCandidate(File home, File hash, Version version, File realHome) {
					this.home = home;
					this.hash = hash;
					this.realHome = realHome;
					this.version = version;
				}
			}

			return Arrays.stream(files).map(it -> {

				File[] hashes = it.listFiles();
				File hash = hashes != null && hashes.length == 1 ? hashes[0] : null;

				Matcher matcher = CANDIDATE.matcher(it.getName());
				if (!matcher.find()) {
					throw new IllegalArgumentException("Cannot determine Maven version number from Maven Wrapper candidate name "
							+ it.getName() + ". This should not happen in an ideal world, check the CANDIDATE regex.");
				}
				String candidateVersion = matcher.group(1);
				Version version = Version.parse(candidateVersion);

				return new WrapperCandidate(it, hash, version, hash != null ? new File(hash, "apache-maven-" + version) : null);

			}).filter(it -> {

				if (it.hash == null) {
					return false;
				}

				return isDirectory(it.realHome);
			}).map(it -> new MavenInstallation(it.version, it.realHome)).collect(Collectors.toList());
		}

	}

	/**
	 * Detector using the {@code java.home} system property.
	 */
	static class MavenHomeEnvironmentDetector implements MavenDetector {

		private static final String maven_home_property = System.getProperty("maven.home");
		private static final @Nullable MavenHomeJdkDetectorSupport mavenHome = StringUtils.hasText(maven_home_property)
				? new MavenHomeJdkDetectorSupport(new File(maven_home_property))
				: null;
		private static final String MAVEN_HOME_ENV = System.getenv("MAVEN_HOME");
		private static final @Nullable MavenHomeJdkDetectorSupport MAVEN_HOME = StringUtils.hasText(MAVEN_HOME_ENV)
				? new MavenHomeJdkDetectorSupport(new File(MAVEN_HOME_ENV))
				: null;

		@Override
		public boolean isAvailable() {
			return hasMavenHomeProperty() || hasMavenHomeEnv();
		}

		private boolean hasMavenHomeProperty() {
			return StringUtils.hasText(maven_home_property) && mavenHome != null;
		}

		private boolean hasMavenHomeEnv() {
			return StringUtils.hasText(MAVEN_HOME_ENV) && MAVEN_HOME != null;
		}

		@Override
		public List<MavenInstallation> detect(JavaRuntimes.JdkInstallation jdk) {

			List<MavenInstallation> installations = new ArrayList<>();

			if (hasMavenHomeProperty()) {
				installations.addAll(mavenHome.detect(jdk));
			}

			if (hasMavenHomeEnv()) {
				installations.addAll(MAVEN_HOME.detect(jdk));
			}

			return installations;
		}
	}

	/**
	 * Detector using a Maven Home directory.
	 */
	static class MavenHomeJdkDetectorSupport implements MavenDetector {

		private final File mavenHome;

		public MavenHomeJdkDetectorSupport(File mavenHome) {
			this.mavenHome = mavenHome;
		}

		@Override
		public boolean isAvailable() {
			return mavenHome != null && isDirectory(mavenHome);
		}

		@Override
		public List<MavenInstallation> detect(JavaRuntimes.JdkInstallation jdk) {

			SimpleMavenRuntime mavenRuntime = new SimpleMavenRuntime(mavenHome, jdk);
			return Collections.singletonList(new MavenInstallation(Version.parse(mavenRuntime.getVersion()), mavenHome));
		}
	}

	@Value
	public static class MavenInstallation implements Comparable<MavenInstallation> {

		Version version;
		File home;

		@Override
		public int compareTo(MavenInstallation o) {
			return this.version.compareTo(o.version);
		}

		@Override
		public String toString() {
			return "Version " + version + " at " + home;
		}
	}

	public static class NoSuchMavenRuntimeException extends NoSuchElementException {

		private final List<MavenInstallation> installations;

		private final String requiredMavenVersion;

		public NoSuchMavenRuntimeException(String message, List<MavenInstallation> installations,
				String requiredMavenVersion) {
			super(message);
			this.installations = installations;
			this.requiredMavenVersion = requiredMavenVersion;
		}

		public List<MavenInstallation> getInstallations() {
			return installations;
		}

		public String getRequiredMavenVersion() {
			return requiredMavenVersion;
		}
	}

	static class NoSuchMavenRuntimeExceptionFailureAnalyzer extends AbstractFailureAnalyzer<NoSuchMavenRuntimeException> {

		@Override
		protected FailureAnalysis analyze(Throwable rootFailure, NoSuchMavenRuntimeException cause) {

			String action = "  Make sure to install %s using your platform installation method or SDKman.%n%n"
					+ "  Detected Maven Runtimes are: %n" + "%s";

			StringBuilder detectedRuntimes = new StringBuilder();

			for (MavenInstallation installation : cause.getInstallations()) {
				detectedRuntimes.append(String.format("    - %-10s %s%n", installation.getVersion(), installation.getHome()));
			}

			return new FailureAnalysis("⚠️ A required Maven version was not found: " + cause.getRequiredMavenVersion(),
					String.format(action, cause.getRequiredMavenVersion(), detectedRuntimes), cause);
		}
	}

	static class SimpleMavenRuntime extends MavenRuntimeSupport {

		/**
		 * Creates a new {@link MavenRuntimeSupport}
		 *
		 * @param mavenHome
		 * @param jdk
		 */
		public SimpleMavenRuntime(File mavenHome, JavaRuntimes.JdkInstallation jdk) {
			super(mavenHome, null, jdk);
		}
	}

}
