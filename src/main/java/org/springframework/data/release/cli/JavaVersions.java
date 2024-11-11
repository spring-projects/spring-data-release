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
package org.springframework.data.release.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.springframework.data.release.io.JavaRuntimes;
import org.springframework.data.release.model.JavaVersion;

/**
 * Value object to encapsulate expected Java versions.
 *
 * @author Mark Paluch
 */
class JavaVersions {

	private final List<String> expectedVersions;

	public JavaVersions(List<String> expectedVersions) {
		this.expectedVersions = expectedVersions;
	}

	/**
	 * Parse the Java versions from the given {@link Properties} at the key {@code jdks}.
	 *
	 * @param properties
	 * @return
	 */
	public static List<String> parse(Properties properties) {

		String jdksProperty = properties.getProperty("jdks");
		return Arrays.asList(jdksProperty.split(","));
	}

	/**
	 * Retrieve the required Java Installation for the given {@code version}.
	 *
	 * @param version
	 * @return
	 */
	public JavaRuntimes.JdkInstallation getInstallation(String version) {
		return getInstallation(JavaVersion.of(version.trim()));
	}

	/**
	 * Retrieve the required Java Installation for the given {@link JavaVersion}.
	 *
	 * @param version
	 * @return
	 */
	public JavaRuntimes.JdkInstallation getInstallation(JavaVersion version) {
		return JavaRuntimes.Selector.notGraalVM(version).getRequiredJdkInstallation();
	}

	public List<String> getExpectedVersions() {
		return this.expectedVersions;
	}
}
