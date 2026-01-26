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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

/**
 * Central place for managing {@link Project projects} within a release train.
 * <p>
 * When adding a new {@link Project} make sure to set the {@link Project#withDependencies(Project...)} and do not forget
 * to add it to the list of projects, defining the dependency order, below. Also add a new {@link Module} to the
 * {@link ReleaseTrains}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class Projects {

	public static final Project BOM, COMMONS, BUILD, REST, JDBC, RELATIONAL, JPA, MONGO_DB, NEO4J, SOLR, COUCHBASE,
			CASSANDRA, ELASTICSEARCH, R2DBC, REDIS, KEY_VALUE, ENVERS, LDAP, GEODE;
	public static final List<Project> PROJECTS;

	public static final Project SMOKE_TESTS = create("Smoke Tests", it -> {});
	public static final Project RELEASE = create("Release", ProjectBuilder::useShortVersionMilestones);

	static {

		BOM = create("BOM", ProjectBuilder::useShortVersionMilestones);

		BUILD = create("Build", it -> it.withAdditionalArtifacts(SpringDataNamingStrategy.SPRING_DATA_BUILD, "build",
				"parent", "build-parent", "build-resources").andArtifact(SpringDataNamingStrategy.SPRING_DATA, "releasetrain"));

		COMMONS = create("Commons", it -> it.dependsOn(BUILD));

		JPA = create("JPA", it -> it.dependsOn(COMMONS).withAdditionalArtifacts(SpringDataNamingStrategy.SPRING_DATA,
				"envers", "jpa-parent", "jpa-distribution"));

		MONGO_DB = create("MongoDB", it -> it.dependsOn(COMMONS)
				.withAdditionalArtifacts(SpringDataNamingStrategy.SPRING_DATA, "mongodb-parent", "mongodb-distribution"));

		NEO4J = create("Neo4j", it -> it.dependsOn(BUILD).withMaintainer(ProjectMaintainer.COMMUNITY));

		SOLR = create("Solr", it -> it.dependsOn(BUILD).withFullName("Spring Data for Apache Solr"));

		COUCHBASE = create("Couchbase", it -> it.dependsOn(BUILD).withMaintainer(ProjectMaintainer.COMMUNITY));

		CASSANDRA = create("Cassandra",
				it -> it.dependsOn(COMMONS)
						.withAdditionalArtifacts(SpringDataNamingStrategy.SPRING_DATA, "cassandra-parent", "cassandra-distribution")
						.withFullName("Spring Data for Apache Cassandra"));

		ELASTICSEARCH = create("Elasticsearch", it -> it.dependsOn(BUILD).withMaintainer(ProjectMaintainer.COMMUNITY));

		KEY_VALUE = create("KeyValue", it -> it.dependsOn(COMMONS));

		REDIS = create("Redis", it -> it.dependsOn(KEY_VALUE));

		JDBC = create("JDBC", it -> it.dependsOn(COMMONS).withAdditionalArtifacts(SpringDataNamingStrategy.SPRING_DATA,
				"relational", "relational-parent", "jdbc"));

		RELATIONAL = create("Relational",
				it -> it.dependsOn(COMMONS).withAdditionalArtifacts(SpringDataNamingStrategy.SPRING_DATA, "relational",
						"relational-parent", "jdbc", "jdbc-distribution", "r2dbc"));

		R2DBC = create("R2DBC", it -> it.dependsOn(COMMONS, JDBC));

		GEODE = create("Geode", it -> it.dependsOn(COMMONS).withAdditionalArtifacts(SpringDataNamingStrategy.SPRING_DATA,
				"relational", "geode-parent", "geode-distribution").withFullName("Spring Data for Apache Geode"));

		REST = create("REST",
				it -> it.dependsOn(JPA, MONGO_DB, NEO4J, CASSANDRA, KEY_VALUE).withAdditionalArtifacts(
						SpringDataNamingStrategy.SPRING_DATA, "relational", "rest-parent", "rest-distribution", "core", "webmvc",
						"hal-browser", "hal-explorer"));

		ENVERS = create("Envers", it -> it.dependsOn(JPA));

		LDAP = create("LDAP", it -> it.dependsOn(COMMONS));

		// Specify build order to avoid maven dependency errors during build.
		List<Project> projects = Arrays.asList(BUILD, COMMONS, JPA, JDBC, RELATIONAL, MONGO_DB, NEO4J, SOLR, COUCHBASE,
				CASSANDRA, ELASTICSEARCH, REDIS, REST, KEY_VALUE, ENVERS, LDAP, GEODE, R2DBC);

		DefaultDirectedGraph<Project, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		projects.forEach(project -> {

			graph.addVertex(project);

			project.getDependencies().forEach(dependency -> {
				graph.addVertex(dependency);
				graph.addEdge(project, dependency);
			});
		});

		List<Project> intermediate = new ArrayList<>(projects.size());
		new TopologicalOrderIterator<>(graph).forEachRemaining(it -> intermediate.add(it));

		Collections.reverse(intermediate);

		PROJECTS = Collections.unmodifiableList(intermediate);
	}

	public static Optional<Project> byName(String name) {

		return Stream.concat(Stream.of(BOM), PROJECTS.stream()) //
				.filter(project -> project.getName().equalsIgnoreCase(name))//
				.findFirst();
	}

	public static Project requiredByName(String name) {

		return byName(name).//
				orElseThrow(() -> new IllegalArgumentException(String.format("No project named %s available!", name)));
	}

	public static List<Project> all() {
		return PROJECTS.stream().collect(Collectors.toList());
	}

	public static List<Project> all(SupportStatus status) {
		return PROJECTS.stream().filter(it -> {
			return matches(it, status);
		}).collect(Collectors.toList());
	}

	static boolean matches(Project project, SupportStatus status) {

		if (status.isEndOfLife()) {
			return project == Projects.SOLR;
		} else {

			if (matches(project, SupportStatus.EOL)) {
				return false;
			}

			if (status.isOpenSource()) {
				return project != Projects.ENVERS && project != Projects.R2DBC && project != Projects.JDBC
						&& project != Projects.GEODE;
			}

			return true;
		}
	}

	/**
	 * Creates a new {@link Project} using the given name and applying the configuration defined in the {@link Consumer}.
	 *
	 * @param name project name.
	 * @param builderConsumer consumer for the builder.
	 * @return the built project.
	 */
	public static Project create(String name, Consumer<ProjectBuilder> builderConsumer) {
		return ProjectBuilder.create(name, builderConsumer);
	}

	/**
	 * Builder for {@link Project}s.
	 */
	static class ProjectBuilder {

		private final SpringDataNamingStrategy namingStrategy = SpringDataNamingStrategy.INSTANCE;
		private final Tracker issueTracker = Tracker.GITHUB;
		private final List<Project> dependencies = new ArrayList<>();

		private ProjectMaintainer maintainer = ProjectMaintainer.CORE;
		private ArtifactCoordinates additionalArtifacts = SpringDataNamingStrategy.SPRING_DATA;
		private boolean useShortVersionMilestones = false;
		private String fullName = null;

		/**
		 * Creates a new {@link Project} using the given name and applying the configuration defined in the
		 * {@link Consumer}.
		 *
		 * @param name project name.
		 * @param builderConsumer consumer for the builder.
		 * @return the built project.
		 */
		public static Project create(String name, Consumer<ProjectBuilder> builderConsumer) {

			ProjectBuilder builder = new ProjectBuilder();
			builderConsumer.accept(builder);

			return new Project(name, (builder.fullName == null ? builder.namingStrategy.getFullName(name) : builder.fullName),
					builder.dependencies, builder.issueTracker, builder.additionalArtifacts, true,
					builder.useShortVersionMilestones, builder.maintainer);
		}

		/**
		 * Set a full name for the project different from the default one to be used when showing a human-readable name.
		 *
		 * @param fullName the full project name.
		 * @return this builder.
		 */
		public ProjectBuilder withFullName(String fullName) {
			this.fullName = fullName;
			return this;
		}

		/**
		 * Associate additional artifacts with the project using the given groupId and simple artifact names. Simple
		 * artifact names are be expanded using the {@link ArtifactNamingStrategy#getArtifactName(String)}.
		 *
		 * @param groupId the groupId to use
		 * @param simpleArtifactNames the simple artifact names.
		 * @return this builder.
		 */
		public ProjectBuilder withAdditionalArtifacts(ArtifactCoordinates groupId, String... simpleArtifactNames) {

			List<String> list = Arrays.stream(simpleArtifactNames).map(namingStrategy::getArtifactName).toList();
			additionalArtifacts = groupId.artifacts(list.toArray(new String[0]));
			return this;
		}

		/**
		 * Associate an additional artifact with the project using the given groupId and simple artifact name. Simple
		 * artifact names are be expanded using the {@link ArtifactNamingStrategy#getArtifactName(String)}.
		 *
		 * @param groupId the groupId to use
		 * @param simpleArtifactName the simple artifact name.
		 * @return this builder.
		 */
		public ProjectBuilder andArtifact(ArtifactCoordinates groupId, String simpleArtifactName) {

			additionalArtifacts = additionalArtifacts
					.and(ArtifactCoordinate.of(groupId.getGroupId(), namingStrategy.getArtifactName(simpleArtifactName)));

			return this;
		}

		/**
		 * Add a dependency on the given projects.
		 *
		 * @param dependencies the dependencies to add.
		 * @return this builder.
		 */
		public ProjectBuilder dependsOn(Project... dependencies) {
			this.dependencies.addAll(Arrays.asList(dependencies));
			return this;
		}

		/**
		 * Use short version milestones like {@code 2.3.0-RC1} instead of {@code 2.3 RC1}.
		 *
		 * @return this builder.
		 */
		public ProjectBuilder useShortVersionMilestones() {
			this.useShortVersionMilestones = true;
			return this;
		}

		/**
		 * Set the maintainer for the project.
		 *
		 * @param maintainer the maintainer to be set.
		 * @return this builder.
		 */
		public ProjectBuilder withMaintainer(ProjectMaintainer maintainer) {
			this.maintainer = maintainer;
			return this;
		}

	}

}
