/*
 * Copyright 2022-2022 the original author or authors.
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
package org.springframework.data.release.io;

import lombok.SneakyThrows;
import lombok.Value;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.system.SystemProperties;
import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.model.Version;
import org.springframework.data.util.Lazy;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.XMLPropertyListParser;

/**
 * Utility to detect a Java runtime version.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public class JavaRuntimes {

	private static final List<JdkDetector> DETECTORS = Arrays.asList(new SDKmanJdkDetector(), new MacNativeJdkDetector(),
			new JavaHomeJdkDetector());
	private static final Lazy<List<JdkInstallation>> JDKS = Lazy.of(() -> {

		List<JdkInstallation> jdks = DETECTORS.stream() //
				.filter(JdkDetector::isAvailable) //
				.flatMap(it -> it.detect().stream()) //
				.sorted() //
				.collect(Collectors.toList());

		Collections.reverse(jdks);

		return Collections.unmodifiableList(jdks);
	});

	/**
	 * Lookup a {@link JdkInstallation} by detecting installed JDKs and applying the {@link Predicate filter}. Returns the
	 * first matching one or throws {@link NoSuchElementException}.
	 *
	 * @param filter
	 * @return
	 */
	public static JdkInstallation getJdk(Predicate<JdkInstallation> filter) {
		return getJdk(filter, "Java Runtime", () -> "Cannot obtain required JDK");
	}

	/**
	 * Lookup a {@link JdkInstallation} by detecting installed JDKs and applying the {@link Predicate filter}. Returns the
	 * first matching one or throws {@link NoSuchElementException}.
	 *
	 * @param filter
	 * @param runtimeName
	 * @param message
	 * @return
	 */
	public static JdkInstallation getJdk(Predicate<JdkInstallation> filter, String runtimeName,
			Supplier<String> message) {

		List<JdkInstallation> jdks = JDKS.get();

		return jdks.stream().filter(filter).findFirst()
				.orElseThrow(() -> new NoSuchJavaRuntimeException(String.format("%s%nAvailable JDK: %s", message.get(), jdks),
						jdks, runtimeName));
	}

	public static List<JdkInstallation> getJdks() {
		return JDKS.get();
	}

	static boolean isDirectory(File file) {
		return file.exists() && file.isDirectory();
	}

	/**
	 * JDK detection strategy.
	 */
	interface JdkDetector {

		/**
		 * @return {@code true} if the detector strategy is available.
		 */
		boolean isAvailable();

		/**
		 * @return a list of JDK installations.
		 */
		List<JdkInstallation> detect();

	}

	/**
	 * Selector to determine a {@link JdkInstallation}.
	 */
	public static class Selector {

		private String notFoundMessage;

		private String javaRuntimeName;
		private Predicate<JdkInstallation> predicate;

		private Selector() {

		}

		public static Selector builder() {
			return new Selector();
		}

		public static Selector from(JavaVersion javaVersion) {

			return builder()
					.and(it -> javaVersion.getVersionDetector().test(it.getVersion())
							&& javaVersion.getImplementor().test(it.getImplementor()))
					.name(javaVersion.getName()).message("Cannot find required " + javaVersion.getName());
		}

		public static Selector notGraalVM(JavaVersion javaVersion) {
			return from(javaVersion).notGraalVM();
		}

		public Selector and(Predicate<JdkInstallation> predicate) {
			this.predicate = this.predicate == null ? predicate : this.predicate.and(predicate);
			return this;
		}

		public Selector notGraalVM() {
			this.notFoundMessage += " (Not GraalVM)";
			return and(it -> !it.getName().contains("GraalVM"));
		}

		public Selector message(String notFoundMessage) {
			this.notFoundMessage = notFoundMessage;
			return this;
		}

		public Selector name(String javaRuntimeName) {
			this.javaRuntimeName = javaRuntimeName;
			return this;
		}

		public JdkInstallation getRequiredJdkInstallation() {
			return JavaRuntimes.getJdk(predicate, javaRuntimeName, () -> notFoundMessage);
		}

	}

	/**
	 * Detector using the SDKman utility storing Java installations in {@code ~/.sdkman/candidates/java}.
	 */
	static class SDKmanJdkDetector implements JdkDetector {

		private static final File sdkManJavaHome;

		private static final Pattern CANDIDATE = Pattern.compile("(\\d+[\\.\\d+]+)[.-][-a-zA-Z]*");

		static {

			if (System.getenv().containsKey("SDKMAN_CANDIDATES_DIR")) {
				sdkManJavaHome = new File(System.getenv().get("SDKMAN_CANDIDATES_DIR"), "java");
			} else if (System.getenv().containsKey("SDKMAN_DIR")) {
				sdkManJavaHome = new File(System.getenv().get("SDKMAN_DIR"), "candidates/java");
			} else {
				sdkManJavaHome = new File(FileUtils.getUserDirectoryPath(), ".sdkman/candidates/java");
			}
		}

		@Override
		public boolean isAvailable() {
			return isDirectory(sdkManJavaHome);
		}

		@Override
		public List<JdkInstallation> detect() {

			File[] files = sdkManJavaHome.listFiles((FileFilter) new RegexFileFilter(CANDIDATE));

			return Arrays.stream(files).map(it -> {

				Matcher matcher = CANDIDATE.matcher(it.getName());
				if (!matcher.find()) {
					throw new IllegalArgumentException("Cannot determine JVM version number from SDKman candidate name "
							+ it.getName() + ". This should not happen in an ideal world, check the CANDIDATE regex.");
				}

				String candidateVersion = matcher.group(1);
				Version version = Version.parse(candidateVersion);
				if (version.getMajor() <= 8) {
					candidateVersion = "1." + candidateVersion;
					version = Version.parse(candidateVersion);
				}

				String implementor = normalizeImplementor(parseImplementor(it));

				return new JdkInstallation(version, toDisplayName(implementor, candidateVersion), implementor, it);

			}).collect(Collectors.toList());
		}

		@SneakyThrows
		private String parseImplementor(File candidateHome) {

			File releaseMeta = new File(candidateHome, "release");
			if (releaseMeta.exists()) {
				List<String> release = FileUtils.readLines(releaseMeta);

				for (String line : release) {

					if (line.startsWith("IMPLEMENTOR=")) {
						String substring = line.substring(line.indexOf("=\""));
						substring = substring.substring(2, substring.length() - 1);
						return substring;
					}
				}
			}

			if (candidateHome.getName().endsWith("-zulu")) {
				return "Azul Systems, Inc.";
			}

			return "?";
		}
	}

	/**
	 * Detector using the {@code java.home} system property.
	 */
	static class JavaHomeJdkDetector implements JdkDetector {

		private static final File javaHome = new File(System.getProperty("java.home"));
		private static final String javaVersion = System.getProperty("java.version");
		private static final String javaVendor = System.getProperty("java.vendor");

		@Override
		public boolean isAvailable() {
			return isDirectory(javaHome);
		}

		@Override
		public List<JdkInstallation> detect() {

			return Collections.singletonList(new JdkInstallation(JavaVersion.parse(javaVersion),
					toDisplayName(javaVendor, javaVersion), normalizeImplementor(javaVendor), javaHome));
		}
	}

	/**
	 * Detector using the {@code /usr/libexec/java_home} utility storing Java installations in {@code /Libraries/Java} on
	 * the Mac.
	 */
	static class MacNativeJdkDetector implements JdkDetector {

		private static final File javaHomeBinary = new File("/usr/libexec/java_home");

		private static final File nativeInstallationDirectory = new File("/Library/Java/JavaVirtualMachines");

		private static final Pattern VERSION = Pattern.compile("((:?\\d+(:?\\.\\d+)*)(:?_+\\d+)?)");

		@Override
		public boolean isAvailable() {
			return isDirectory(nativeInstallationDirectory) && !ObjectUtils.isEmpty(nativeInstallationDirectory.listFiles())
					&& javaHomeBinary.exists() && SystemProperties.get("os.name").contains("Mac");
		}

		@Override
		@SneakyThrows
		public List<JdkInstallation> detect() {

			Process process = new ProcessBuilder(javaHomeBinary.toString(), "-X").redirectOutput(ProcessBuilder.Redirect.PIPE)
					.start();

			process.waitFor(5, TimeUnit.SECONDS);
			byte[] out = StreamUtils.copyToByteArray(process.getInputStream());

			if (process.exitValue() != 0) {

				throw new IllegalStateException(javaHomeBinary + " failed with: " + System.lineSeparator() + new String(out)
						+ new String(StreamUtils.copyToByteArray(process.getErrorStream())));
			}

			NSArray array = (NSArray) XMLPropertyListParser.parse(new ByteArrayInputStream(out));

			return Arrays.stream(array.getArray()).map(it -> {

				NSDictionary dict = (NSDictionary) it;

				String jvmHomePath = dict.get("JVMHomePath").toJavaObject(String.class);
				String name = dict.get("JVMName").toJavaObject(String.class);
				String version = dict.get("JVMVersion").toJavaObject(String.class);
				String vendor = dict.get("JVMVendor").toJavaObject(String.class);

				Matcher matcher = VERSION.matcher(version);
				if (!matcher.find()) {
					throw new IllegalArgumentException("Cannot determine JVM version number from JVMVersion " + version
							+ ". This should not happen in an ideal world, check the VERSION regex.");
				}

				String implementor = normalizeImplementor(vendor);
				return new JdkInstallation(JavaVersion.parse(matcher.group(1)), toDisplayName(implementor, version),
						implementor, new File(jvmHomePath));

			}).collect(Collectors.toList());
		}
	}

	@Value
	public static class JdkInstallation implements Comparable<JdkInstallation> {

		Version version;
		String name;
		String implementor;
		File home;

		@Override
		public int compareTo(JdkInstallation o) {
			return this.version.compareTo(o.version);
		}
	}

	static String normalizeImplementor(String implementor) {

		if (implementor.equals("Eclipse Adoptium")) {
			return "Eclipse Temurin";
		}

		if (implementor.equals("Eclipse Foundation")) {
			return "Eclipse Temurin";
		}

		return implementor;
	}

	static String toDisplayName(String implementor, String version) {

		if (implementor.startsWith("Oracle")) {
			implementor = "Oracle Java";
		}

		return implementor + " " + version;
	}

	public static class NoSuchJavaRuntimeException extends NoSuchElementException {

		private final List<JdkInstallation> installations;

		private final String requiredJdk;

		public NoSuchJavaRuntimeException(String message, List<JdkInstallation> installations, String requiredJdk) {
			super(message);
			this.installations = installations;
			this.requiredJdk = requiredJdk;
		}

		public List<JdkInstallation> getInstallations() {
			return installations;
		}

		public String getRequiredJdk() {
			return requiredJdk;
		}
	}

	static class NoSuchJavaRuntimeExceptionFailureAnalyzer extends AbstractFailureAnalyzer<NoSuchJavaRuntimeException> {

		@Override
		protected FailureAnalysis analyze(Throwable rootFailure, NoSuchJavaRuntimeException cause) {

			String action = "  Make sure to install %s using your platform installation method or SDKman.%n%n"
					+ "  Detected Java Runtimes are: %n" + "%s";

			StringBuilder detectedRuntimes = new StringBuilder();

			for (JdkInstallation installation : cause.getInstallations()) {
				detectedRuntimes.append(String.format("    - %-20s %-10s %s%n", installation.getImplementor(),
						installation.getVersion(), installation.getHome()));
			}

			return new FailureAnalysis("⚠️ A required JDK was not found: " + cause.getRequiredJdk(),
					String.format(action, cause.getRequiredJdk(), detectedRuntimes), cause);
		}
	}

}
