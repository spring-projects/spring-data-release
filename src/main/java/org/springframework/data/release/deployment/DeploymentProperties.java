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

import lombok.Data;

import java.net.URI;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.release.model.Gpg;
import org.springframework.data.release.model.Password;
import org.springframework.data.release.model.SupportStatusAware;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriTemplate;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Data
@Component
@ConfigurationProperties(prefix = "deployment")
public class DeploymentProperties implements InitializingBean {

	private String settingsXml;
	private MavenCentral mavenCentral;
	private Authentication opensource, commercial;

	public Authentication getAuthentication(SupportStatusAware status) {
		return status.isCommercial() ? commercial : opensource;
	}

	public Streamable<Authentication> getAuthentications() {
		return Streamable.of(opensource, commercial);
	}

	@Override
	public void afterPropertiesSet() {

		Assert.notNull(mavenCentral, "Maven Central properties not provided");
		Assert.notNull(opensource, "OSS authentication properties not provided");
		Assert.notNull(commercial, "Commercial authentication properties not provided");

		mavenCentral.validate();
		opensource.validate();
		commercial.validate();
	}

	@Data
	public static class Server {

		private static final String PROMOTION_RESOURCE = "/api/build/promote/{buildName}/{buildNumber}?project={project}";
		private static final String DELETE_BUILD_RESOURCE = "/api/build/{buildName}?buildNumbers={buildNumber}&artifacts=1&project={project}";
		private static final String VERIFICATION_RESOURCE = "/api/storage/{verificationResource}";

		private String uri;
		private String verificationResource;

		/**
		 * Returns the URI to the resource that a promotion can be triggered at.
		 *
		 * @param information must not be {@literal null}.
		 * @return
		 */
		public URI getPromotionResource(DeploymentInformation information) {

			Assert.notNull(information, "DeploymentInformation must not be null!");

			return new UriTemplate(uri.concat(PROMOTION_RESOURCE)).expand(information.getBuildInfoParameters());
		}

		public URI getDeleteBuildResource(DeploymentInformation information) {

			return new UriTemplate(uri.concat(DELETE_BUILD_RESOURCE)).expand(information.getBuildInfoParameters());
		}

		public URI getVerificationResource() {
			return new UriTemplate(uri.concat(VERIFICATION_RESOURCE)).expand(verificationResource);
		}
	}

	@Data
	public static class MavenCentral {

		private String stagingProfileId;

		private Publishing process;

		private Gpg gpg;

		public boolean hasGpgConfiguration() {
			return gpg != null && gpg.isGpgAvailable();
		}

		public void validate() {

			if (!StringUtils.hasText(stagingProfileId)) {
				throw new IllegalArgumentException("No staging profile Id for Maven Central");
			}
		}

		public enum Publishing {
			OSSRH, PUBLISHER
		}
	}

	@Data
	public static class Authentication {

		Server server;
		String stagingRepository, targetRepository;
		String distributionRepository;
		String project;
		String username;
		Password password;
		String apiKey;
		String repositoryPrefix = "";

		public boolean hasCredentials() {
			return StringUtils.hasText(username) && password != null;
		}

		public void validate() {

			if (!StringUtils.hasText(stagingRepository)) {
				throw new IllegalArgumentException(
						String.format("No staging repository for server authentication %s provided", server));
			}

			if (!StringUtils.hasText(targetRepository)) {
				throw new IllegalArgumentException(
						String.format("No target repository for server authentication %s provided", server));
			}

			if (!StringUtils.hasText(distributionRepository)) {
				throw new IllegalArgumentException(
						String.format("No distribution repository for server authentication %s provided", server));
			}

			if (!StringUtils.hasText(server.uri)) {
				throw new IllegalArgumentException(
						String.format("No server URI for server authentication %s provided", server));
			}

			if (!StringUtils.hasText(server.verificationResource)) {
				throw new IllegalArgumentException(
						String.format("No verification resource for server authentication %s provided", server));
			}
		}

		/**
		 * Returns the URI of the staging repository.
		 *
		 * @return
		 */
		public String getStagingRepositoryUrl() {
			return server.getUri().concat("/").concat(stagingRepository);
		}

		public String getStagingRepository() {
			return repositoryPrefix.concat(stagingRepository);
		}

		public String getDistributionRepository() {
			return repositoryPrefix.concat(distributionRepository);
		}
	}
}
