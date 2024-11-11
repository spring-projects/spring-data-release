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

import lombok.Value;

import java.util.Properties;

import org.springframework.data.release.model.Version;

/**
 * Value object for a Maven version.
 *
 * @author Mark Paluch
 */
@Value(staticConstructor = "of")
class MavenVersion {

	String expectedVersion;

	/**
	 * Parse the Maven version from the given {@link Properties} at the key {@code maven}.
	 *
	 * @param properties
	 * @return
	 */
	public static MavenVersion parse(Properties properties) {
		return of(properties.getProperty("maven"));
	}

	public Version getExpectedVersion() {
		return Version.parse(this.expectedVersion);
	}

	@Override
	public String toString() {
		return expectedVersion;
	}
}
