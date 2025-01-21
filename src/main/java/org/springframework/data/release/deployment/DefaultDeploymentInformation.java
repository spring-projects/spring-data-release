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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.release.deployment.DeploymentProperties.Authentication;
import org.springframework.data.release.git.Branch;
import org.springframework.data.release.git.GitProject;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.util.StringUtils;

/**
 * Information about a deployment.
 *
 * @author Oliver Gierke
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class DefaultDeploymentInformation implements DeploymentInformation {

	private final @Getter @NonNull ModuleIteration module;
	private final @NonNull DeploymentProperties properties;
	private final @Getter String buildNumber;
	private final @Getter StagingRepository stagingRepositoryId;

	private final Authentication authentication;

	public DefaultDeploymentInformation(ModuleIteration module, DeploymentProperties properties) {
		this(module, properties, StagingRepository.EMPTY);
	}

	public DefaultDeploymentInformation(ModuleIteration module, DeploymentProperties properties,
			String stagingRepositoryId) {
		this(module, properties, createBuildNumber(module), StagingRepository.of(stagingRepositoryId),
				properties.getAuthentication(module));
	}

	public DefaultDeploymentInformation(ModuleIteration module, DeploymentProperties properties,
			StagingRepository stagingRepository) {
		this(module, properties, createBuildNumber(module), stagingRepository, properties.getAuthentication(module));
	}

	public static String createBuildNumber(ModuleIteration module) {

		String actualBuildNumber;

		if (StringUtils.hasText(System.getenv("BUILD_ID"))) {
			actualBuildNumber = System.getenv("BUILD_ID");
		} else {
			actualBuildNumber = "" + LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

		}

		Branch branch;
		if (module.isBranchVersion() || module.isCommercial() || module.getIteration().isServiceIteration()) {
			branch = Branch.from(module.getVersion());
		} else {
			branch = Branch.MAIN;
		}

		return String.format("%s-%s-release-%s", getBuildName(module.getSupportedProject()), branch, actualBuildNumber);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.deployment.DeploymentInformation#getBuildName()
	 */
	@Override
	public String getBuildName() {
		return getBuildName(module.getSupportedProject());
	}

	private static String getBuildName(SupportedProject project) {
		return GitProject.of(project).getRepositoryName();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.deployment.DeploymentInformation#getProject()
	 */
	@Override
	public String getProject() {
		return authentication.getProject();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.deployment.DeploymentInformation#getTargetRepository()
	 */
	@Override
	public String getTargetRepository() {
		return authentication.getRepositoryPrefix().concat(authentication.getTargetRepository());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.deployment.DeploymentInformation#getBuildInfoParameters()
	 */
	@Override
	public Map<String, Object> getBuildInfoParameters() {

		Map<String, Object> parameters = new HashMap<>();
		parameters.put("buildNumber", getBuildNumber());
		parameters.put("buildName", getBuildName());

		String project = StringUtils.hasText(getProject()) ? getProject() : "";
		parameters.put("project", project);

		return parameters;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.deployment.DeploymentInformation#getPromotionResource()
	 */
	@Override
	public URI getPromotionResource() {
		return authentication.getServer().getPromotionResource(this);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.deployment.DeploymentInformation#getDeleteBuildResource()
	 */
	@Override
	public URI getDeleteBuildResource() {
		return authentication.getServer().getDeleteBuildResource(this);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.deployment.DeploymentInformation#isMavenCentral()
	 */
	@Override
	public boolean isMavenCentral() {
		return !module.isCommercial() && module.getIteration().isPublic();
	}
}
