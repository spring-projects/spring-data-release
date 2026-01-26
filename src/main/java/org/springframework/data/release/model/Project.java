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
package org.springframework.data.release.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.beans.ConstructorProperties;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.util.Assert;

/**
 * @author Oliver Gierke
 */
@ToString
@EqualsAndHashCode
public class Project implements Comparable<Project>, Named {

	private static final GitHubNamingStrategy NAMING_STRATEGY = SpringDataNamingStrategy.INSTANCE;

	private final @Getter String name;
	private final @Getter String fullName;
	private final Collection<Project> dependencies;
	private final @Getter Tracker tracker;
	private final ArtifactCoordinates additionalArtifacts;
	private final boolean skipTests;
	private final @Getter boolean useShortVersionMilestones; // use a short version 2.3.0-RC1 instead of 2.3 RC1 if
	private final @Getter ProjectMaintainer maintainer;
	// true

	@ConstructorProperties({ "name", "fullName", "dependencies", "tracker", "additionalArtifacts",
			"skipTests", "plainVersionMilestones", "owner" })
	Project(String name, String fullName, Collection<Project> dependencies, Tracker tracker,
			ArtifactCoordinates additionalArtifacts, boolean skipTests, boolean useShortVersionMilestones,
			ProjectMaintainer maintainer) {

		this.name = name;
		this.fullName = fullName;
		this.dependencies = dependencies;
		this.tracker = tracker;
		this.additionalArtifacts = additionalArtifacts;
		this.skipTests = skipTests;
		this.useShortVersionMilestones = useShortVersionMilestones;
		this.maintainer = maintainer;
	}

	public boolean uses(Tracker tracker) {
		return this.tracker.equals(tracker);
	}

	public String getFolderName() {
		return NAMING_STRATEGY.getRepository(getName(), SupportStatus.OSS);
	}

	public String getDependencyProperty() {
		return "springdata.".concat(name.toLowerCase());
	}

	public void doWithAdditionalArtifacts(Consumer<ArtifactCoordinate> consumer) {
		additionalArtifacts.getCoordinates().forEach(consumer);
	}

	public Module toModule(String version) {
		return new Module(this, version);
	}

	/**
	 * Returns whether the current project depends on the given one.
	 *
	 * @param project must not be {@literal null}.
	 * @return
	 */
	public boolean dependsOn(Project project) {

		Assert.notNull(project, "Project must not be null!");

		return dependencies.stream().anyMatch(dependency -> dependency.equals(project) || dependency.dependsOn(project));
	}

	public boolean skipTests() {
		return this.skipTests;
	}

	public Project withDependencies(Project... project) {
		return new Project(name, fullName, Arrays.asList(project), tracker, additionalArtifacts, skipTests,
				useShortVersionMilestones, maintainer);
	}

	/**
	 * Returns all dependencies of the current project including transitive ones.
	 *
	 * @return
	 */
	public Set<Project> getDependencies() {

		return dependencies.stream() //
				.flatMap(dependency -> Stream.concat(Stream.of(dependency), dependency.getDependencies().stream())) //
				.collect(Collectors.toSet());
	}

	public GitHubNamingStrategy getNamingStrategy() {
		return NAMING_STRATEGY;
	}

	public String getProjectDescriptor() {
		return this == Projects.BUILD ? "parent/pom.xml" : "pom.xml";
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Project that) {
		return Projects.PROJECTS.indexOf(this) - Projects.PROJECTS.indexOf(that);
	}

}
