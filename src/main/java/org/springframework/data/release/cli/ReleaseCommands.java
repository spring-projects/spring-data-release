/*
 * Copyright 2014-2022 the original author or authors.
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
package org.springframework.data.release.cli;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.release.CliComponent;
import org.springframework.data.release.TimedCommand;
import org.springframework.data.release.build.BuildOperations;
import org.springframework.data.release.deployment.DeploymentInformation;
import org.springframework.data.release.deployment.DeploymentOperations;
import org.springframework.data.release.deployment.StagingRepository;
import org.springframework.data.release.git.BranchMapping;
import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.issues.IssueTrackerCommands;
import org.springframework.data.release.issues.github.GitHubCommands;
import org.springframework.data.release.misc.ReleaseOperations;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Phase;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.util.Assert;

/**
 * @author Oliver Gierke
 * @author Greg Turnquist
 */
@CliComponent
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class ReleaseCommands extends TimedCommand {

	@NonNull GitOperations git;
	@NonNull ReleaseOperations misc;
	@NonNull DeploymentOperations deployment;
	@NonNull BuildOperations build;
	@NonNull IssueTrackerCommands tracker;
	@NonNull GitHubCommands gitHub;

	/**
	 * Composite command to prepare a release.
	 *
	 * @param iteration
	 * @throws Exception
	 */
	@CliCommand(value = "prepare-it")
	public void prepareIt(@CliOption(key = "", mandatory = true) TrainIteration iteration) throws Exception {

		tracker.trackerPrepare(iteration);

		prepare(iteration);

		build.build(iteration);

		conclude(iteration);

		gitHub.push(iteration);
	}

	/**
	 * Composite command to ship a full release.
	 *
	 * @param iteration
	 * @throws Exception
	 */
	@CliCommand(value = "ship-it")
	public void shipIt(@CliOption(key = "", mandatory = true) TrainIteration iteration) throws Exception {

		tracker.trackerPrepare(iteration);

		prepare(iteration);

		conclude(iteration);

		buildRelease(iteration, null);

		distribute(iteration, null);

		gitHub.push(iteration);

		tracker.closeIteration(iteration);
	}

	/**
	 * Prepares the release of the given iteration of the given train.
	 *
	 * @param iteration
	 * @throws Exception
	 */
	@CliCommand(value = "release prepare", help = "Prepares the release of the iteration of the given train.")
	public void prepare(@CliOption(key = "", mandatory = true) TrainIteration iteration) throws Exception {

		git.prepare(iteration);

		build.runPreReleaseChecks(iteration);

		misc.updateResources(iteration);
		build.updateProjectDescriptors(iteration, Phase.PREPARE);
		git.commit(iteration, "Prepare %s.");

		build.prepareVersions(iteration, Phase.PREPARE);
		git.commit(iteration, "Release version %s.");
	}

	@CliCommand(value = "repository open")
	public void repositoryOpen(@CliOption(key = "", mandatory = true) TrainIteration iteration) {

		if (iteration.isPublic()) {
			build.open(iteration.getModule(Projects.BUILD));
		}
	}

	@CliCommand(value = "repository close")
	public void repositoryClose(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "stagingRepositoryId", mandatory = true) String stagingRepositoryId) {

		if (iteration.isPublic()) {
			build.close(iteration.getModule(Projects.BUILD), StagingRepository.of(stagingRepositoryId));
		}
	}

	@CliCommand(value = "release repository")
	public void repositoryRelease(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "stagingRepositoryId", mandatory = true) String stagingRepositoryId) {

		if (iteration.isPublic()) {
			build.release(iteration.getModule(Projects.BUILD), StagingRepository.of(stagingRepositoryId));
		}
	}

	@CliCommand(value = "release build")
	public void buildRelease(@CliOption(key = "", mandatory = true) TrainIteration iteration, //
			@CliOption(key = "project", mandatory = false) String projectName) {

		git.checkout(iteration);

		if (!iteration.isPublic()) {
			deployment.verifyAuthentication(iteration);
		}

		if (projectName != null) {

			Project project = Projects.requiredByName(projectName);
			ModuleIteration module = iteration.getModule(project);

			DeploymentInformation information = build.performRelease(module);
			deployment.promote(information);

		} else {
			build.performRelease(iteration).forEach(deployment::promote);
		}
	}

	/**
	 * Concludes the release of the given {@link TrainIteration}.
	 *
	 * @param iteration
	 * @throws Exception
	 */
	@CliCommand(value = "release conclude")
	public void conclude(@CliOption(key = "", mandatory = true) TrainIteration iteration) throws Exception {

		Assert.notNull(iteration, "Train iteration must not be null!");

		// Tag release
		git.tagRelease(iteration);

		if (iteration.getTrain().isAlwaysUseBranch()) {
			setupMaintenanceVersions(iteration, BranchMapping.NONE, true);
		} else {

			build.prepareVersions(iteration, Phase.CLEANUP);
			git.commit(iteration, "Prepare next development iteration.");

			// Prepare main branch
			build.updateProjectDescriptors(iteration, Phase.CLEANUP);
			git.commit(iteration, "After release cleanups.");

			// Prepare maintenance branches
			if (iteration.getIteration().isGAIteration()) {

				// Create bugfix branches
				BranchMapping branches = git.createMaintenanceBranches(iteration,
						iteration.getTrain().getIteration(Iteration.SR1));

				// Set project version to maintenance once
				setupMaintenanceVersions(iteration, branches, true);
			}
		}
	}

	@CliCommand(value = "release create-branches")
	public void createBranches(@CliOption(key = "from", mandatory = true) TrainIteration from,
			@CliOption(key = "to", mandatory = true) TrainIteration to) throws Exception {

		if (!to.getTrain().isAlwaysUseBranch()) {
			throw new IllegalArgumentException(
					String.format("Cannot create branches as train %s does not use branches.", to.getTrain().getCalver()));
		}

		git.prepare(from);

		// Create bugfix branches
		BranchMapping branchMapping = git.createBranch(from, to);

		// Set project version to maintenance once
		setupMaintenanceVersions(to, branchMapping, false);
	}

	@CliCommand(value = "release documentation")
	public void triggerDocumentation(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "module", mandatory = false) String module) {

		Set<Project> skip = new HashSet<>(Arrays.asList(Projects.BOM, Projects.BUILD, Projects.R2DBC, Projects.JDBC,
				Projects.SOLR, Projects.GEODE, Projects.SMOKE_TESTS));

		iteration.forEach(it -> {

			Project project = it.getProject();

			if (skip.contains(project)) {
				return;
			}

			if (module == null || module.equalsIgnoreCase(project.getName())) {
				gitHub.triggerAntoraWorkflow(project);
			}
		});
	}

	private void setupMaintenanceVersions(TrainIteration iteration, BranchMapping branches, boolean checkoutIteration)
			throws Exception {

		// Set project version to maintenance once
		build.prepareVersions(iteration, Phase.MAINTENANCE);
		git.commit(iteration, "Prepare next development iteration.");

		// Update inter-project dependencies and repositories
		build.updateProjectDescriptors(iteration, Phase.MAINTENANCE);

		if (branches.hasBranches()) {
			build.updateBuildConfig(iteration, branches);
		}

		git.commit(iteration, "After release cleanups.");

		if (checkoutIteration) {
			// Back to main branch
			git.checkout(iteration);
		}
	}

	/**
	 * Triggers the smoke tests.
	 *
	 * @param iteration
	 * @throws Exception
	 */
	@CliCommand(value = "release smoke-tests")
	public void runSmokeTests(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "stagingRepository", mandatory = false) String stagingRepository) throws Exception {
		build.smokeTests(iteration,
				stagingRepository != null ? StagingRepository.of(stagingRepository) : StagingRepository.EMPTY);
	}

	/**
	 * Triggers the distribution of release artifacts for all projects.
	 *
	 * @param iteration
	 * @throws Exception
	 */
	@CliCommand("release distribute")
	public void distribute(@CliOption(key = "", mandatory = true) TrainIteration iteration,
			@CliOption(key = "project", mandatory = false) String projectName) {

		git.checkout(iteration);

		if (projectName != null) {
			Project project = Projects.requiredByName(projectName);
			ModuleIteration module = iteration.getModule(project);

			build.distributeResources(module);
		} else {
			build.distributeResources(iteration);
		}
	}
}
