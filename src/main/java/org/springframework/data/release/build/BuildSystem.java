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
package org.springframework.data.release.build;

import org.springframework.data.release.deployment.DeploymentInformation;
import org.springframework.data.release.deployment.StagingRepository;
import org.springframework.data.release.model.JavaVersion;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Phase;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.ProjectAware;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.plugin.core.Plugin;

/**
 * Plugin interface to back different build systems.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 * @author Greg Turnquist
 */
interface BuildSystem extends Plugin<SupportedProject> {

	/**
	 * Updates the project descriptors for the given {@link ModuleIteration} using the given {@link UpdateInformation}.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param updateInformation must not be {@literal null}.
	 */
	<M extends ProjectAware> M updateProjectDescriptors(M iteration, UpdateInformation updateInformation);

	/**
	 * Prepares the project descriptor of the {@link ModuleIteration} for the given release {@link Phase}.
	 *
	 * @param module must not be {@literal null}.
	 * @param phase must not be {@literal null}.
	 * @return
	 */
	ModuleIteration prepareVersion(ModuleIteration module, Phase phase);

	/**
	 * Triggers the pre-release checks for the given {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	<M extends ProjectAware> M triggerPreReleaseCheck(M module);

	/**
	 * Open a remote repository for staging artifacts.
	 *
	 * @param train must not be {@literal null}.
	 */
	StagingRepository open(Train train);

	/**
	 * Close a remote repository for staging artifacts.
	 *
	 * @param train must not be {@literal null}.
	 */
	void close(Train train, StagingRepository stagingRepository);

	/**
	 * Release a remote repository of staged artifacts.
	 *
	 * @param train must not be {@literal null}.
	 */
	void release(Train train, StagingRepository stagingRepository);

	<M extends ProjectAware> M triggerBuild(M module);

	<M extends ProjectAware> M triggerDocumentationBuild(M module);

	/**
	 * Deploy artifacts for the given {@link ModuleIteration} using {@link DeploymentInformation}.
	 *
	 * @param module must not be {@literal null}.
	 * @param stagingRepository must not be {@literal null}.
	 * @return
	 */
	DeploymentInformation deploy(ModuleIteration module, StagingRepository stagingRepository);

	/**
	 * Deploy artifacts for the given {@link ModuleIteration} and return the {@link DeploymentInformation}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	DeploymentInformation deploy(ModuleIteration module);

	/**
	 * Run smoke test against a {@link StagingRepository}.
	 *
	 * @param iteration
	 * @param stagingRepository
	 */
	void smokeTests(TrainIteration iteration, StagingRepository stagingRepository);

	/**
	 * Runs the distribution build.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	<M extends ProjectAware> M triggerDistributionBuild(M module);

	/**
	 * Verify general functionality and correctness of the build setup.
	 *
	 * @param train must not be {@literal null}.
	 */
	void verify(Train train);

	/**
	 * Verify general functionality and correctness of the build setup.
	 *
	 * @param train must not be {@literal null}.
	 */
	void verifyStagingAuthentication(Train train);

	/**
	 * Prepare the build system with a Java version.
	 *
	 * @param javaVersion
	 * @return
	 */
	BuildSystem withJavaVersion(JavaVersion javaVersion);
}
