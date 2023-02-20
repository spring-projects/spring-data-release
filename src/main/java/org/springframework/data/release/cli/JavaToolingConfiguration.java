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

import java.util.Properties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.release.build.MavenProperties;
import org.springframework.data.release.build.MavenRuntime;
import org.springframework.data.release.utils.Logger;

/**
 * Configuration to verify build infrastructure.
 *
 * @author Mark Paluch
 */
@Configuration
class JavaToolingConfiguration {

	private static final Resource javaTools = new FileSystemResource("ci/java-tools.properties");

	@Bean
	PropertiesFactoryBean javaTools() {

		if (!javaTools.exists()) {
			throw new IllegalStateException(String.format("%s does not exist", javaTools));
		}

		PropertiesFactoryBean factory = new PropertiesFactoryBean();
		factory.setLocations(javaTools);

		return factory;
	}

	@Bean
	JavaToolingVerifier verifier(@Qualifier("javaTools") Properties javaTools, MavenRuntime mavenRuntime,
			MavenProperties mavenProperties, Logger logger) {
		return new JavaToolingVerifier(javaTools, mavenRuntime, mavenProperties, logger);
	}
}
