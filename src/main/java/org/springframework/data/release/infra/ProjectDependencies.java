/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.data.release.infra;

import lombok.Value;

import java.util.Iterator;
import java.util.List;

import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.util.Streamable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Configuration of dependencies for a specific project.
 *
 * @author Mark Paluch
 * @author Jens Schauder
 */
public class ProjectDependencies implements Streamable<ProjectDependencies.ProjectDependency> {

	private static final MultiValueMap<Project, ProjectDependency> config = new LinkedMultiValueMap<>();

	static {

		config.add(Projects.BUILD, ProjectDependency.ofProperty("apt", Dependencies.APT));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("aspectj", Dependencies.ASPECTJ));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("assertj", Dependencies.ASSERTJ));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("jackson", Dependencies.JACKSON));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("jacoco", Dependencies.JACOCO));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("jodatime", Dependencies.JODA_TIME));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("junit5", Dependencies.JUNIT5));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("jmolecules", Dependencies.JMOLECULES));
		config.add(Projects.BUILD,
				ProjectDependency.ofProperty("jmolecules-integration", Dependencies.JMOLECULES_INTEGRATION));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("junit", Dependencies.JUNIT4));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("kotlin", Dependencies.KOTLIN));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("kotlin-coroutines", Dependencies.KOTLIN_COROUTINES));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("micrometer", Dependencies.MICROMETER));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("micrometer-tracing", Dependencies.MICROMETER_TRACING));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("mockito", Dependencies.MOCKITO));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("mockk", Dependencies.MOCKK));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("querydsl", Dependencies.QUERYDSL));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("rxjava", Dependencies.RXJAVA1));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("rxjava2", Dependencies.RXJAVA2));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("rxjava3", Dependencies.RXJAVA3));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("rxjava-reactive-streams", Dependencies.RXJAVA_RS));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("smallrye-mutiny", Dependencies.SMALLRYE_MUTINY));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("spring-hateoas", Dependencies.SPRING_HATEOAS));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("spring-plugin", Dependencies.SPRING_PLUGIN));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("testcontainers", Dependencies.TESTCONTAINERS));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("threetenbp", Dependencies.THREE_TEN_BP));
		config.add(Projects.BUILD, ProjectDependency.ofProperty("webbeans", Dependencies.OPEN_WEB_BEANS));

		config.add(Projects.COMMONS, ProjectDependency.ofProperty("vavr", Dependencies.VAVR));
		config.add(Projects.COMMONS, ProjectDependency.ofProperty("xmlbeam", Dependencies.XML_BEAM));

		config.add(Projects.JPA, ProjectDependency.ofProperty("hibernate", Dependencies.HIBERNATE));
		config.add(Projects.JPA, ProjectDependency.ofProperty("eclipselink", Dependencies.ECLIPSELINK));

		config.add(Projects.MONGO_DB, ProjectDependency.ofProperty("mongo.reactivestreams", Dependencies.MONGODB_RS));
		config.add(Projects.MONGO_DB, ProjectDependency.ofProperty("mongo", Dependencies.MONGODB_LEGACY));
		config.add(Projects.MONGO_DB, ProjectDependency.ofProperty("mongo", Dependencies.MONGODB_CORE));
		config.add(Projects.MONGO_DB, ProjectDependency.ofProperty("mongo", Dependencies.MONGODB_SYNC));
		config.add(Projects.MONGO_DB, ProjectDependency.ofProperty("mongo", Dependencies.MONGODB_ASYNC));

		config.add(Projects.REDIS, ProjectDependency.ofProperty("lettuce", Dependencies.LETTUCE));
		config.add(Projects.REDIS, ProjectDependency.ofProperty("jedis", Dependencies.JEDIS));

		config.add(Projects.CASSANDRA,
				ProjectDependency.ofProperty("cassandra-driver.version", Dependencies.CASSANDRA_DRIVER3));
		config.add(Projects.CASSANDRA,
				ProjectDependency.ofProperty("cassandra-driver.version", Dependencies.CASSANDRA_DRIVER4));

		config.add(Projects.NEO4J, ProjectDependency.ofProperty("neo4j.ogm.version", Dependencies.NEO4J_OGM));
		config.add(Projects.NEO4J, ProjectDependency.ofProperty("neo4j-java-driver.version", Dependencies.NEO4J_DRIVER));

		config.add(Projects.COUCHBASE, ProjectDependency.ofProperty("couchbase", Dependencies.COUCHBASE));

		config.add(Projects.ELASTICSEARCH, ProjectDependency.ofProperty("elasticsearch", Dependencies.ELASTICSEARCH_RHLC));
		config.add(Projects.ELASTICSEARCH,
				ProjectDependency.ofProperty("elasticsearch-rhlc", Dependencies.ELASTICSEARCH_RHLC));
		config.add(Projects.ELASTICSEARCH,
				ProjectDependency.ofProperty("elasticsearch-java", Dependencies.ELASTICSEARCH_REST_CLIENT));

		config.add(Projects.LDAP, ProjectDependency.ofProperty("spring-ldap", Dependencies.SPRING_LDAP));

		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("h2.version", Dependencies.H2));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("hsqldb.version", Dependencies.HSQLDB));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("db2.version", Dependencies.DB2_JDBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("mariadb-java-client.version", Dependencies.MARIADB_JDBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("mssql.version", Dependencies.MS_SQLSERVER_JDBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("mysql-connector-java.version", Dependencies.MYSQL_JDBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("postgresql.version", Dependencies.POSTGRES_JDBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("oracle.version", Dependencies.ORACLE_JDBC));

		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("r2dbc-postgresql.version", Dependencies.POSTGRES_R2DBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("r2dbc-h2.version", Dependencies.H2_R2DBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("r2dbc-mariadb.version", Dependencies.MARIADB_R2DBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("r2dbc-mssql.version", Dependencies.MS_SQLSERVER_R2DBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("r2dbc-mysql.version", Dependencies.MYSQL_R2DBC));
		config.add(Projects.RELATIONAL, ProjectDependency.ofProperty("oracle-r2dbc.version", Dependencies.ORACLE_R2DBC));
	}

	private final List<ProjectDependency> dependencies;

	private ProjectDependencies(List<ProjectDependency> dependencies) {
		this.dependencies = dependencies;
	}

	/**
	 * Retrieve upgradable dependencies for a {@link SupportedProject}.
	 *
	 * @param project
	 * @return
	 * @throws IllegalArgumentException if the project has no upgradable dependencies.
	 */
	public static ProjectDependencies get(SupportedProject project) {

		if (!containsProject(project.getProject())) {
			throw new IllegalArgumentException(String.format("No dependency configuration for %s!", project));
		}

		return new ProjectDependencies(config.get(project.getProject()));
	}

	/**
	 * Check whether the {@link Project} has upgradable dependencies.
	 *
	 * @param project
	 * @return
	 */
	public static boolean containsProject(Project project) {
		return config.containsKey(project);
	}

	public String getVersionPropertyFor(Dependency dependency) {

		for (ProjectDependency projectDependency : dependencies) {

			if (projectDependency.getDependency().equals(dependency)) {
				return projectDependency.getProperty();
			}
		}

		throw new IllegalArgumentException("Dependency " + dependency + " is not a dependency of this project!");
	}

	@Override
	public Iterator<ProjectDependency> iterator() {
		return dependencies.iterator();
	}

	@Value
	public static class ProjectDependency {

		String property;

		Dependency dependency;

		public static ProjectDependency ofProperty(String pomProperty, Dependency dependency) {
			return new ProjectDependency(pomProperty, dependency);
		}
	}
}
