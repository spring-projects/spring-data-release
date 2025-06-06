/*
 * Copyright 2015-2022 the original author or authors.
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
package org.springframework.data.release.deployment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.release.deployment.DeploymentProperties.Authentication;
import org.springframework.data.release.utils.HttpBasicCredentials;
import org.springframework.data.release.utils.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.data.release.utils.Logger;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration to set up deployment components.
 *
 * @author Oliver Gierke
 */
@Configuration(proxyBeanMethods = false)
class DeploymentConfiguration {

	@Autowired DeploymentProperties properties;

	@Bean
	ArtifactoryClient client(Logger logger, RestOperations operations) {
		return new ArtifactoryClient(logger, properties, operations);
	}

	@Bean
	RestTemplate artifactoryRestTemplateFactory(DeploymentProperties properties, Logger logger) {

		HttpComponentsClientHttpRequestFactoryBuilder builder = HttpComponentsClientHttpRequestFactoryBuilder.builder();

		for (Authentication authentication : properties.getAuthentications()) {

			String uri = authentication.getServer().getUri();

			if (authentication.hasCredentials()) {

				HttpBasicCredentials credentials = new HttpBasicCredentials(authentication.getUsername(),
						authentication.getApiKey());
				builder = builder.withAuthentication(uri, credentials);

			} else {
				logger.warn("Infrastructure", "No credentials configured for repository %s!", uri);
			}
		}

		return new RestTemplate(builder.build());
	}
}
