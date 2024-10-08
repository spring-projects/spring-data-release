/*
 * Copyright 2016-2022 the original author or authors.
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

import java.net.URI;
import java.util.Map;

import org.springframework.data.release.model.ModuleIteration;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public interface DeploymentInformation {

	/**
	 * Returns the name of the build.
	 *
	 * @return will never be {@literal null} or empty.
	 */
	String getBuildName();

	/**
	 * Returns a unique build number for this particular deployment.
	 *
	 * @return will never be {@literal null} or empty.
	 */
	String getBuildNumber();

	/**
	 * A project assignment on the server. JFrog uses this to group different builds into build info repositories in
	 * Artifactory.
	 *
	 * @return
	 */
	String getProject();

	/**
	 * Returns the name of the repository to deploy to.
	 *
	 * @return will never be {@literal null} or empty.
	 */
	String getTargetRepository();

	URI getPromotionResource();

	URI getDeleteBuildResource();

	/**
	 * Staging repository identifier.
	 *
	 * @return
	 */
	StagingRepository getStagingRepositoryId();

	/**
	 * Returns the {@link ModuleIteration} the deployment information was created for.
	 *
	 * @return
	 */
	ModuleIteration getModule();

	/**
	 * Returns a {@link Map} to expand a URI template to access the build information.
	 *
	 * @return
	 */
	Map<String, Object> getBuildInfoParameters();

	boolean isMavenCentral();
}
