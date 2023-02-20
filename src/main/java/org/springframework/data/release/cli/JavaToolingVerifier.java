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
package org.springframework.data.release.cli;

import lombok.RequiredArgsConstructor;

import java.util.Properties;

import javax.annotation.PostConstruct;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.data.release.build.MavenProperties;
import org.springframework.data.release.build.MavenRuntime;
import org.springframework.data.release.io.JavaRuntimes.JdkInstallation;
import org.springframework.data.release.io.JavaRuntimes.Selector;
import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.utils.Logger;
import org.springframework.util.StringUtils;

/**
 * Utility to verify early on that your build environment contains all the required Java and Maven versions.
 *
 * @author Mark Paluch
 */
@RequiredArgsConstructor
class JavaToolingVerifier {

	private final Properties javaTools;

	private final MavenRuntime mavenRuntime;

	private final MavenProperties mavenProperties;

	private final Logger logger;

	@PostConstruct
	public void verify() {

		String jdksProperty = javaTools.getProperty("jdks");
		String[] jdks = jdksProperty.split(",");

		logger.log("JavaTooling", "🕵️ Checking presence of JDKs %s…", StringUtils.arrayToDelimitedString(jdks, ", "));

		for (String jdk : jdks) {

			JavaVersion javaVersion = JavaVersion.of(jdk.trim());

			JdkInstallation jdkInstallation = Selector.notGraalVM(javaVersion).getRequiredJdkInstallation();
			logger.log("JavaTooling", "✅ Found %s by %s", javaVersion.getName(), jdkInstallation.getImplementor());
		}

		String expectedMavenVersion = javaTools.getProperty("maven");

		logger.log("JavaTooling", "🕵️ Checking presence of Maven %s…", expectedMavenVersion);

		String installedMavenVersion = mavenRuntime.getVersion();
		if (!expectedMavenVersion.equals(installedMavenVersion)) {
			throw new InvalidMavenVersionException(expectedMavenVersion, installedMavenVersion,
					mavenProperties.getMavenHome());
		}

		logger.log("JavaTooling", "✅ Found Maven %s", installedMavenVersion);
	}

	static class InvalidMavenVersionExceptionFailureAnalyzer
			extends AbstractFailureAnalyzer<InvalidMavenVersionException> {

		@Override
		protected FailureAnalysis analyze(Throwable rootFailure, InvalidMavenVersionException cause) {

			return new FailureAnalysis(
					String.format("⚠️ The configured Maven version %s at %s does not match the required version %s.",
							cause.getActualVersion(), cause.getHome(), cause.getExpectedVersion()),
					String.format("  Make sure to use Maven %s or update your maven.maven-home property.",
							cause.getExpectedVersion()),
					cause);
		}
	}
}
