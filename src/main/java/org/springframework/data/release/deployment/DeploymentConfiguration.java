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
import org.springframework.data.release.model.Password;
import org.springframework.data.release.utils.HttpBasicCredentials;
import org.springframework.data.release.utils.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.data.release.utils.Logger;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
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
	public ArtifactoryClient client(Logger logger, RestTemplate artifactoryRestTemplate) {
		return new ArtifactoryClient(artifactoryRestTemplate, logger, properties);
	}

	@Bean
	public RestTemplate artifactoryRestTemplate() {

		RestTemplate template = new RestTemplate();

		HttpComponentsClientHttpRequestFactory factory = HttpComponentsClientHttpRequestFactoryBuilder.builder()
				.withAuthentication(properties.getServer().getUri(),
						new HttpBasicCredentials(properties.getUsername(), Password.of(properties.getApiKey())))
				.build();

		template.setRequestFactory(factory);

		return template;
	}

}
