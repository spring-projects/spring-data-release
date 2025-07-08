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
package org.springframework.data.release.model;

import lombok.Value;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value object representing a Java version.
 *
 * @author Mark Paluch
 */
@Value(staticConstructor = "of")
public class JavaVersion {

	public static final JavaVersion VERSION_1_8 = of("1.8");

	private static final Pattern DOCKER_TAG_PATTERN = Pattern.compile("((:?\\d+(:?u\\d+)?(:?\\.\\d+)*)).*");

	String name;
	Predicate<Version> versionDetector;
	Predicate<String> implementor;

	public static JavaVersion of(String version) {
		Version expectedVersion = parse(version);
		return of("JDK " + version, candidate -> {

			if (expectedVersion.getBugfix() == 0 && expectedVersion.getBuild() == 0) {
				return candidate.withBugfix(0).is(expectedVersion);
			}

			return candidate.is(expectedVersion);
		}, it -> true);
	}

	public static Version parse(String version) {
		if (version.startsWith("8.")) {
			version = "1." + version;
		}
		return Version.parse(version.replace('_', '.'));
	}

	/**
	 * Parse a docker tag into a Java version using {@code eclipse-temurin} conventions.
	 *
	 * @param tagName
	 * @return
	 */
	public static JavaVersion fromDockerTag(String tagName) {

		Pattern versionExtractor = Pattern.compile("(:?\\d+(:?u\\d+)?(:?\\.\\d+)*).*");
		Matcher matcher = versionExtractor.matcher(tagName);
		if (!matcher.find()) {
			throw new IllegalStateException("Cannot parse Java version '%s'".formatted(tagName));
		}

		String plainVersion = matcher.group(1);

		if (plainVersion.startsWith("8u")) {
			return of("1.8.0_" + plainVersion.substring(2));
		}

		return of(plainVersion).withImplementor("Temurin");
	}

	public JavaVersion withImplementor(String implementor) {
		return of("%s (%s)".formatted(name, implementor), versionDetector, it -> it.contains(implementor));
	}
}
