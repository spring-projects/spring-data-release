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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

/**
 * Central place for managing {@link Project projects} within a release train.
 * <p />
 * When adding a new {@link Project} make sure to set the {@link Project#withDependencies(Project...)} and do not forget
 * to add it to the list of projects, defining the dependency order, below. <br />
 * Also add a new {@link Module} to the {@link ReleaseTrains}.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class Projects {

	public static final Project BOM, COMMONS, BUILD, REST, JDBC, RELATIONAL, JPA, MONGO_DB, NEO4J, SOLR, COUCHBASE,
			CASSANDRA, ELASTICSEARCH, R2DBC, REDIS, KEY_VALUE, ENVERS, LDAP, GEODE;
	public static final List<Project> PROJECTS;
	public static final Project SMOKE_TESTS = new Project("SMOKE_TESTS", "Smoke Tests", Tracker.GITHUB);

	public static final Project RELEASE = new Project("RELEASE", "Release", Tracker.GITHUB)
			.withUseShortVersionMilestones(true);

	static {

		BOM = new Project("DATABOM", "BOM", Tracker.GITHUB).withUseShortVersionMilestones(true);

		BUILD = new Project("DATABUILD", "Build", Tracker.GITHUB) //
				.withAdditionalArtifacts(ArtifactCoordinates.forGroupId("org.springframework.data.build")
						.artifacts("spring-data-build", "spring-data-parent", "spring-data-build-parent",
								"spring-data-build-resources")
						.and(ArtifactCoordinate.of("org.springframework.data", "spring-data-releasetrain")));

		COMMONS = new Project("DATACMNS", "Commons", Tracker.GITHUB).withDependencies(BUILD);

		JPA = new Project("DATAJPA", "JPA", Tracker.GITHUB) //
				.withDependencies(COMMONS) //
				.withAdditionalArtifacts(ArtifactCoordinates.SPRING_DATA.artifacts("spring-data-envers",
						"spring-data-jpa-parent", "spring-data-jpa-distribution"));

		MONGO_DB = new Project("DATAMONGO", "MongoDB", Tracker.GITHUB) //
				.withDependencies(COMMONS) //
				.withAdditionalArtifacts(ArtifactCoordinates.SPRING_DATA.artifacts("spring-data-mongodb-parent",
						"spring-data-mongodb-distribution"));

		NEO4J = new Project("DATAGRAPH", "Neo4j", Tracker.GITHUB).withDependencies(COMMONS)
				.withMaintainer(ProjectMaintainer.COMMUNITY);

		SOLR = new Project("DATASOLR", "Solr", Tracker.GITHUB) //
				.withDependencies(COMMONS) //
				.withFullName("Spring Data for Apache Solr");

		COUCHBASE = new Project("DATACOUCH", "Couchbase", Tracker.GITHUB).withDependencies(COMMONS)
				.withMaintainer(ProjectMaintainer.COMMUNITY);

		CASSANDRA = new Project("DATACASS", "Cassandra", Tracker.GITHUB) //
				.withDependencies(COMMONS) //
				.withAdditionalArtifacts(ArtifactCoordinates.SPRING_DATA.artifacts("spring-data-cassandra-parent",
						"spring-data-cassandra-distribution"))
				.withFullName("Spring Data for Apache Cassandra");

		ELASTICSEARCH = new Project("DATAES", "Elasticsearch", Tracker.GITHUB).withDependencies(COMMONS)
				.withMaintainer(ProjectMaintainer.COMMUNITY);

		KEY_VALUE = new Project("DATAKV", "KeyValue", Tracker.GITHUB).withDependencies(COMMONS);

		REDIS = new Project("DATAREDIS", "Redis", Tracker.GITHUB).withDependencies(KEY_VALUE);

		JDBC = new Project("DATAJDBC", "JDBC", Tracker.GITHUB).withAdditionalArtifacts(ArtifactCoordinates.SPRING_DATA
				.artifacts("spring-data-relational", "spring-data-relational-parent", "spring-data-jdbc"))
				.withDependencies(COMMONS);

		RELATIONAL = new Project("DATAJDBC", "Relational", Tracker.GITHUB)
				.withAdditionalArtifacts(ArtifactCoordinates.SPRING_DATA.artifacts("spring-data-relational",
						"spring-data-relational-parent", "spring-data-jdbc", "spring-data-jdbc-distribution", "spring-data-r2dbc"))
				.withDependencies(COMMONS);

		R2DBC = new Project("DATAR2DBC", "R2DBC", Tracker.GITHUB).withDependencies(COMMONS, JDBC);

		GEODE = new Project("DATAGEODE", "Geode", Tracker.GITHUB) //
				.withDependencies(COMMONS) //
				.withAdditionalArtifacts(ArtifactCoordinates.SPRING_DATA.artifacts("spring-data-gemfire"))
				.withFullName("Spring Data for Apache Geode") //
				.withSkipTests(true);

		REST = new Project("DATAREST", "REST", Tracker.GITHUB) //
				.withDependencies(JPA, MONGO_DB, NEO4J, CASSANDRA, KEY_VALUE) //
				.withAdditionalArtifacts(ArtifactCoordinates.SPRING_DATA //
						.artifacts("spring-data-rest-parent", "spring-data-rest-core", "spring-data-rest-core",
								"spring-data-rest-webmvc", "spring-data-rest-hal-browser", "spring-data-rest-distribution",
								"spring-data-rest-hal-explorer"));

		ENVERS = new Project("DATAENV", "Envers", Tracker.GITHUB).withDependencies(JPA);

		LDAP = new Project("DATALDAP", "LDAP", Tracker.GITHUB).withDependencies(COMMONS);

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
				.filter(project -> project.getName().equalsIgnoreCase(name) || project.getKey().toString().equals(name))//
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

}
