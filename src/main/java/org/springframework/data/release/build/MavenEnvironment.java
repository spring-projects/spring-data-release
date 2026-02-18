/*
 * Copyright 2026-present the original author or authors.
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

import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.model.SupportedProject;

/**
 * Interface to execute Maven builds in a given environment.
 *
 * @author Mark Paluch
 */
public interface MavenEnvironment {

	/**
	 * Configure the environment to use the given Java version. This is required to execute Maven builds with a specific
	 * Java version.
	 *
	 * @param javaVersion
	 * @return a new MavenEnvironment configured to use the given Java version.
	 */
	MavenEnvironment withJavaVersion(JavaVersion javaVersion);

	/**
	 * Execute a Maven build for the given project and arguments.
	 *
	 * @param project
	 * @param arguments
	 * @return
	 */
	MavenInvocationResult execute(SupportedProject project, CommandLine arguments);
}
