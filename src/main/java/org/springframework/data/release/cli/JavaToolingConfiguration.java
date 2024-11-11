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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.release.build.MavenProperties;
import org.springframework.data.release.build.MavenRuntime;
import org.springframework.data.release.build.MavenRuntimes;
import org.springframework.data.release.io.JavaRuntimes;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.utils.Logger;
import org.springframework.util.StringUtils;

/**
 * Configuration to verify build infrastructure.
 *
 * @author Mark Paluch
 */
@Configuration
@RequiredArgsConstructor
class JavaToolingConfiguration {

	private static final Resource javaTools = new FileSystemResource("ci/java-tools.properties");

	private final Workspace workspace;
	private final Logger logger;

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
	JavaVersions javaVersions(@Qualifier("javaTools") Properties javaTools) {

		JavaVersions javaVersions = new JavaVersions(JavaVersions.parse(javaTools));

		logger.log("JavaTooling", "🕵️ Checking presence of JDKs %s…",
				StringUtils.collectionToDelimitedString(javaVersions.getExpectedVersions(), ", "));

		for (String jdk : javaVersions.getExpectedVersions()) {

			JavaVersion javaVersion = JavaVersion.of(jdk.trim());

			JavaRuntimes.JdkInstallation jdkInstallation = javaVersions.getInstallation(javaVersion);
			logger.log("JavaTooling", "✅ Found %s by %s", javaVersion.getName(), jdkInstallation.getImplementor());
		}

		return javaVersions;
	}

	@Bean
	MavenVersion mavenVersion(@Qualifier("javaTools") Properties javaTools) {
		return MavenVersion.parse(javaTools);
	}

	@Bean
	public MavenRuntime mavenRuntime(JavaVersions javaVersions, MavenVersion mavenVersion, MavenProperties properties) {

		logger.log("JavaTooling", "🕵️ Checking presence of Maven %s…", mavenVersion.getExpectedVersion());

		String firstJdk = javaVersions.getExpectedVersions().get(0);
		JavaRuntimes.JdkInstallation installation = javaVersions.getInstallation(firstJdk);

		MavenRuntimes.Selector selector;
		if (properties.getMavenHome() != null) {
			selector = MavenRuntimes.Selector.builder(MavenRuntimes.detector(properties.getMavenHome()));
		} else {
			selector = MavenRuntimes.Selector.builder();
		}

		MavenRuntimes.MavenInstallation mavenInstallation = selector.version(mavenVersion.getExpectedVersion())
				.getRequiredMavenInstallation(installation);

		logger.log("JavaTooling", "✅ Found Maven %s", mavenInstallation);

		return new MavenRuntime(workspace, logger, mavenInstallation, properties);
	}

}
