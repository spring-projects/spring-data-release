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

import lombok.Getter;

import java.io.File;

/**
 * @author Mark Paluch
 */
@Getter
class InvalidMavenVersionException extends IllegalStateException {

	private final String expectedVersion;
	private final String actualVersion;

	private final File home;

	public InvalidMavenVersionException(String expectedVersion, String installedVersion, File home) {
		super(String.format("Invalid Maven version: Expected %s, found version %s", expectedVersion, installedVersion));
		this.expectedVersion = expectedVersion;
		this.actualVersion = installedVersion;
		this.home = home;
	}
}
