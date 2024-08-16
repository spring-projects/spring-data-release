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

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.data.release.deployment.DeploymentProperties.Authentication;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.SupportStatusAware;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A client to interact with Artifactory.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@RequiredArgsConstructor
class ArtifactoryClient {

	private final static String CREATE_RELEASE_BUNDLE_PATH = "/lifecycle/api/v2/release_bundle?project=spring";
	private final static String DISTRIBUTE_RELEASE_BUNDLE_PATH = "/lifecycle/api/v2/distribution/distribute/{releaseBundle}/{version}?project=spring";

	private final Logger logger;
	private final DeploymentProperties properties;
	private final RestOperations operations;

	/**
	 * Triggers the promotion of the artifacts identified by the given {@link DeploymentInformation}.
	 *
	 * @param information must not be {@literal null}.
	 */
	public void promote(DeploymentInformation information) {

		Assert.notNull(information, "DeploymentInformation must not be null!");

		ModuleIteration module = information.getModule();
		URI uri = information.getPromotionResource();

		Authentication authentication = properties.getAuthentication(module);

		logger.log(module, "Promoting %s %s from %s to %s.", information.getBuildName(), information.getBuildNumber(),
				authentication.getStagingRepository(), authentication.getTargetRepository());

		try {
			PromotionRequest request = new PromotionRequest(information.getTargetRepository(),
					authentication.getStagingRepository());
			operations.postForEntity(uri, request, String.class);

		} catch (HttpClientErrorException o_O) {
			handle(message -> logger.warn(information.getModule(), message), "Promotion failed!", o_O);
		}
	}

	public void verify(SupportStatusAware status) {

		URI verificationResource = properties.getAuthentication(status).getServer().getVerificationResource();

		try {

			logger.log("Artifactory", "Verifying authentication using a GET call to %s.", verificationResource);

			operations.getForEntity(verificationResource, String.class);

			logger.log("Artifactory", "Authentication verified!");

		} catch (HttpClientErrorException o_O) {
			handle(message -> logger.log("Artifactory Client", message), "Authentication verification failed!", o_O);
			throw new IllegalStateException("Authentication verification failed!");
		}
	}

	private void handle(Consumer<Object> logger, String message, HttpClientErrorException o_O) {

		try {

			logger.accept(message);

			Errors errors = new ObjectMapper().readValue(o_O.getResponseBodyAsByteArray(), Errors.class);
			errors.getErrors().forEach(logger);
			errors.getMessages().forEach(logger);

		} catch (IOException e) {
			o_O.addSuppressed(e);
			throw new RuntimeException(o_O.getResponseBodyAsString(), o_O);
		}
	}

	public void deleteArtifacts(DeploymentInformation information) {
		operations.delete(information.getDeleteBuildResource());
	}

	public void createRelease(String context, ArtifactoryReleaseBundle releaseBundle,
			Authentication authentication) {

		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		headers.add("X-JFrog-Signing-Key-Name", "packagesKey");
		HttpEntity<ArtifactoryReleaseBundle> entity = new HttpEntity<>(releaseBundle, headers);

		try {
			ResponseEntity<Map> response = operations
					.postForEntity(authentication.getServer().getUri() + CREATE_RELEASE_BUNDLE_PATH, entity, Map.class);

			if (!response.getStatusCode().is2xxSuccessful()) {
				logger.warn(context, "Artifactory request failed: %d %s", response.getStatusCode().value(),
						response.getBody());
			} else {
				logger.log(context, "Artifactory request succeeded: %s %s", releaseBundle.getName(),
						releaseBundle.getVersion());
			}
		} catch (HttpStatusCodeException e) {
			logger.warn(context, "Artifactory request failed: %d %s", e.getStatusCode().value(),
					e.getResponseBodyAsString());
		}
	}

	public void distributeRelease(TrainIteration train, String releaseName, String version,
			Authentication authentication) {

		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		String body = "{\n" + "\t\"auto_create_missing_repositories\": \"false\",\n" + "\t\"distribution_rules\": [\n"
				+ "\t\t{\n" + "\t\t\t\"site_name\": \"JP-SaaS\"\n" + "\t\t}\n" + "\t],\n" + "\t\"modifications\": {\n"
				+ "\t\t\"mappings\": [\n" + "\t\t\t{\n" + "\t\t\t\t\"input\": \"spring-enterprise-maven-prod-local/(.*)\",\n"
				+ "\t\t\t\t\"output\": \"spring-enterprise/$1\"\n" + "\t\t\t}\n" + "\t\t]\n" + "\t}\n" + "}";
		HttpEntity<String> entity = new HttpEntity<>(body, headers);

		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("releaseBundle", releaseName);
		parameters.put("version", version);

		try {
			ResponseEntity<Map> response = operations
					.postForEntity(authentication.getServer().getUri() + DISTRIBUTE_RELEASE_BUNDLE_PATH, entity, Map.class,
							parameters);

			if (!response.getStatusCode().is2xxSuccessful()) {
				logger.warn(train, "Artifactory request failed: %d %s", response.getStatusCode().value(), response.getBody());
			} else {
				logger.log(train, "Artifactory request succeeded: %s %s", releaseName, version);
			}
		} catch (HttpStatusCodeException e) {
			logger.warn(train, "Artifactory request failed: %d %s", e.getStatusCode().value(), e.getResponseBodyAsString());
		}
	}

	@Value
	static class PromotionRequest {
		String targetRepo, sourceRepo;
	}
}
