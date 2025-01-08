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
package org.springframework.data.release.projectservice;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.data.release.git.GitOperations;
import org.springframework.data.release.git.Tag;
import org.springframework.data.release.model.Module;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.model.Train;
import org.springframework.data.release.model.Version;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.release.utils.ListWrapperCollector;
import org.springframework.data.release.utils.Logger;
import org.springframework.data.util.Streamable;
import org.springframework.util.Assert;

import com.google.common.util.concurrent.MoreExecutors;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class ProjectServiceOperations {

	private static final List<Project> TO_FILTER = Arrays.asList(Projects.COMMONS, //
			Projects.KEY_VALUE);

	GitOperations git;
	Executor executor;
	ProjectService client;
	Logger logger;

	public ProjectServiceOperations(GitOperations git, ProjectService client, Logger logger) {
		this.git = git;
		this.executor = MoreExecutors.directExecutor();
		this.client = client;
		this.logger = logger;
	}

	/**
	 * Updates the project metadata for the modules in the given release {@link Train}s.
	 *
	 * @param trains must not be {@literal null}.
	 */
	void updateProjectMetadata(Train... trains) {

		Assert.notNull(trains, "Trains must not be null!");

		updateProjectMetadata(Arrays.asList(trains));
	}

	void updateProjectMetadata(List<Train> trains) {

		Assert.notNull(trains, "Trains must not be null!");

		List<Train> openSourceTrains = trains.stream()
				.filter(it -> it.getSupportStatus().isOpenSource())
				.collect(Collectors.toList());

		if (openSourceTrains.isEmpty()) {
			return;
		}

		Map<Project, MaintainedVersions> versions = findVersions(openSourceTrains);

		Streamable<Entry<Project, MaintainedVersions>> stream = Streamable.of(versions.entrySet()) //
				.filter(entry -> {
					return entry.getKey() != Projects.BOM && entry.getKey() != Projects.JDBC;
				});

		ExecutionUtils.run(executor, stream, entry -> {

			// Sometimes we see 404 Not Found: [no body], sometimes
			// 400 Bad Request: "Release '2.2.13-SNAPSHOT' already present
			// still we should fail here and report those failures to the website team
			client.updateProjectMetadata(entry.getKey(), entry.getValue(), true, false);
		});

		ExecutionUtils.run(executor, stream, entry -> {

			// Sometimes we see 404 Not Found: [no body], sometimes
			// 400 Bad Request: "Release '2.2.13-SNAPSHOT' already present
			// still we should fail here and report those failures to the website team
			client.updateProjectMetadata(entry.getKey(), entry.getValue(), false, true);
		});
	}

	/**
	 * Returns all {@link MaintainedVersions} grouped by {@link Project} for the given release {@link Train}.
	 *
	 * @param trains must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	Map<Project, MaintainedVersions> findVersions(Train... trains) {

		Assert.notNull(trains, "Trains must not be null!");

		return findVersions(Arrays.asList(trains));
	}

	Map<Project, MaintainedVersions> findVersions(List<Train> trains) {

		Assert.notNull(trains, "Trains must not be null!");

		Map<Project, MaintainedVersions> versions = ExecutionUtils.runAndReturn(executor, Streamable.of(trains), train -> {

			return ExecutionUtils.runAndReturn(executor,
					train.getModules().filter(module -> !TO_FILTER.contains(module.getProject())),
					module -> getLatestVersion(module, train));

		}).stream()
				.flatMap(Collection::stream)
				.flatMap(Collection::stream)
				.collect(
						Collectors.groupingBy(MaintainedVersion::getProject,
								ListWrapperCollector.collectInto(MaintainedVersions::of)));

		// Migration because of the R2DBC merge into Spring Data Relational and project rename to Relational
		versions.put(Projects.R2DBC, MaintainedVersions.of(getR2dbcVersions(versions)));
		versions.put(Projects.RELATIONAL, MaintainedVersions.of(getRelationalVersions(versions)));
		versions.remove(Projects.JDBC);

		return versions;
	}

	/**
	 * Copy Relational versions into R2DBC as we feed two projects (JDBC, R2DBC) from {@link Projects#RELATIONAL}.
	 *
	 * @param versions
	 * @return
	 */
	private List<MaintainedVersion> getR2dbcVersions(Map<Project, MaintainedVersions> versions) {

		List<MaintainedVersion> r2dbcVersions = new ArrayList<>(
				versions.getOrDefault(Projects.R2DBC, MaintainedVersions.of()).toList());

		MaintainedVersions relationalVersions = versions.get(Projects.RELATIONAL);

		for (MaintainedVersion relationalVersion : relationalVersions) {
			if (relationalVersion.getVersion().getVersion().getMajor() >= 3) {
				r2dbcVersions.add(relationalVersion.withProject(Projects.R2DBC));
			}
		}
		return r2dbcVersions;
	}

	/**
	 * Merge JDBC versions into Relational to avoid having two projects mapping to Spring Data JDBC in Sagan.
	 *
	 * @param versions
	 * @return
	 */
	private List<MaintainedVersion> getRelationalVersions(Map<Project, MaintainedVersions> versions) {

		List<MaintainedVersion> relationalVersions = new ArrayList<>(
				versions.getOrDefault(Projects.RELATIONAL, MaintainedVersions.of()).toList());

		if (versions.containsKey(Projects.JDBC)) {

			MaintainedVersions jdbcVersions = versions.get(Projects.JDBC);

			for (MaintainedVersion jdbcVersion : jdbcVersions) {
				if (jdbcVersion.getVersion().getVersion().getMajor() < 3) {
					relationalVersions.add(jdbcVersion.withProject(Projects.RELATIONAL));
				}
			}
		}
		return relationalVersions;
	}

	private List<MaintainedVersion> getLatestVersion(Module module, Train train) {

		SupportedProject project = train.getSupportedProject(module);

		List<MaintainedVersion> version = git.getTags(project).stream()//
				.filter(tag -> matches(tag, module.getVersion())).max(Comparator.naturalOrder()) //
				.map(it -> {
					MaintainedVersion maintainedVersion = MaintainedVersion.of(module.getProject(),
							it.toArtifactVersion().get(),
							train, it.getCreationDate().toLocalDate(), it.getCreationDate().toLocalDate());
					return Arrays.asList(maintainedVersion, maintainedVersion.nextDevelopmentVersion());
				}) //
				.orElseGet(() -> Collections.singletonList(MaintainedVersion.snapshot(module, train, LocalDate.now())));

		logger.log(project, "Found version %s for train %s!", version, train.getName());

		return version;
	}

	/**
	 * Returns whether the given {@link Tag} is one that logically belongs to the given version.
	 *
	 * @param tag must not be {@literal null}.
	 * @param version must not be {@literal null}.
	 * @return
	 */
	private static boolean matches(Tag tag, Version version) {

		return tag.toArtifactVersion()//
				.map(it -> it.isVersionWithin(version))//
				.orElse(false);
	}
}
