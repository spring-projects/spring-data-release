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

		config.add(Projects.BUILD, ProjectDependency.using("apt", Dependencies.APT));
		config.add(Projects.BUILD, ProjectDependency.using("aspectj", Dependencies.ASPECTJ));
		config.add(Projects.BUILD, ProjectDependency.using("assertj", Dependencies.ASSERTJ));
		config.add(Projects.BUILD, ProjectDependency.using("jackson", Dependencies.JACKSON2));
		config.add(Projects.BUILD, ProjectDependency.using("jackson3", Dependencies.JACKSON3));
		config.add(Projects.BUILD, ProjectDependency.using("jacoco", Dependencies.JACOCO));
		config.add(Projects.BUILD, ProjectDependency.using("jodatime", Dependencies.JODA_TIME));
		config.add(Projects.BUILD, ProjectDependency.using("junit5", Dependencies.JUNIT5));
		config.add(Projects.BUILD, ProjectDependency.using("junit-pioneer", Dependencies.JUNIT_PIONEER));
		config.add(Projects.BUILD, ProjectDependency.using("jmh", Dependencies.JMH));
		config.add(Projects.BUILD, ProjectDependency.using("jmolecules", Dependencies.JMOLECULES));
		config.add(Projects.BUILD,
				ProjectDependency.using("jmolecules-integration", Dependencies.JMOLECULES_INTEGRATION));
		config.add(Projects.BUILD, ProjectDependency.using("junit", Dependencies.JUNIT4));
		config.add(Projects.BUILD, ProjectDependency.using("kotlin", Dependencies.KOTLIN));
		config.add(Projects.BUILD, ProjectDependency.using("kotlin-coroutines", Dependencies.KOTLIN_COROUTINES));
		config.add(Projects.BUILD, ProjectDependency.using("logback", Dependencies.LOGBACK));
		config.add(Projects.BUILD, ProjectDependency.using("micrometer", Dependencies.MICROMETER));
		config.add(Projects.BUILD, ProjectDependency.using("micrometer-tracing", Dependencies.MICROMETER_TRACING));
		config.add(Projects.BUILD, ProjectDependency.using("mockito", Dependencies.MOCKITO));
		config.add(Projects.BUILD, ProjectDependency.using("mockk", Dependencies.MOCKK));
		config.add(Projects.BUILD, ProjectDependency.using("querydsl", Dependencies.QUERYDSL));
		config.add(Projects.BUILD, ProjectDependency.using("rxjava", Dependencies.RXJAVA1));
		config.add(Projects.BUILD, ProjectDependency.using("rxjava2", Dependencies.RXJAVA2));
		config.add(Projects.BUILD, ProjectDependency.using("rxjava3", Dependencies.RXJAVA3));
		config.add(Projects.BUILD, ProjectDependency.using("rxjava-reactive-streams", Dependencies.RXJAVA_RS));
		config.add(Projects.BUILD, ProjectDependency.using("slf4j", Dependencies.SLF4J));
		config.add(Projects.BUILD, ProjectDependency.using("smallrye-mutiny", Dependencies.SMALLRYE_MUTINY));
		config.add(Projects.BUILD, ProjectDependency.using("spring-hateoas", Dependencies.SPRING_HATEOAS));
		config.add(Projects.BUILD, ProjectDependency.using("spring-plugin", Dependencies.SPRING_PLUGIN));
		config.add(Projects.BUILD, ProjectDependency.using("testcontainers", Dependencies.TESTCONTAINERS));
		config.add(Projects.BUILD, ProjectDependency.using("threetenbp", Dependencies.THREE_TEN_BP));
		config.add(Projects.BUILD, ProjectDependency.using("webbeans", Dependencies.OPEN_WEB_BEANS));

		config.add(Projects.COMMONS, ProjectDependency.using("vavr", Dependencies.VAVR));
		config.add(Projects.COMMONS, ProjectDependency.using("xmlbeam", Dependencies.XML_BEAM));

		config.add(Projects.JPA, ProjectDependency.managedProperty("hibernate", Dependencies.HIBERNATE));
		config.add(Projects.JPA, ProjectDependency.managedProperty("eclipselink", Dependencies.ECLIPSELINK));
		config.add(Projects.JPA, ProjectDependency.managedProperty("jsqlparser", Dependencies.JSQLPARSER));
		config.add(Projects.JPA, ProjectDependency.managedProperty("postgresql", Dependencies.POSTGRES_JDBC));
		config.add(Projects.JPA, ProjectDependency.managedProperty("mysql-connector-java", Dependencies.MYSQL_JDBC));
		config.add(Projects.JPA, ProjectDependency.managedProperty("h2", Dependencies.H2));
		config.add(Projects.JPA, ProjectDependency.managedProperty("hsqldb", Dependencies.HSQLDB));
		config.add(Projects.JPA, ProjectDependency.managedProperty("antlr", Dependencies.ANTLR));

		config.add(Projects.MONGO_DB, ProjectDependency.managedProperty("awaitility.version", Dependencies.AWAITILITY));
		config.add(Projects.MONGO_DB, ProjectDependency.using("mongo", Dependencies.MONGODB_BOM));
		config.add(Projects.MONGO_DB, ProjectDependency.managedProperty("mongo", Dependencies.MONGODB_CORE));

		config.add(Projects.REDIS, ProjectDependency.managedProperty("awaitility.version", Dependencies.AWAITILITY));
		config.add(Projects.REDIS, ProjectDependency.using("lettuce", Dependencies.LETTUCE));
		config.add(Projects.REDIS, ProjectDependency.using("jedis", Dependencies.JEDIS));

		config.add(Projects.CASSANDRA,
				ProjectDependency.using("cassandra-driver.version", Dependencies.CASSANDRA_DRIVER4));

		config.add(Projects.NEO4J,
				ProjectDependency.managedProperty("neo4j-java-driver.version", Dependencies.NEO4J_DRIVER));

		config.add(Projects.COUCHBASE, ProjectDependency.using("couchbase", Dependencies.COUCHBASE));

		config.add(Projects.ELASTICSEARCH, ProjectDependency.using("elasticsearch", Dependencies.ELASTICSEARCH_RHLC));
		config.add(Projects.ELASTICSEARCH,
				ProjectDependency.using("elasticsearch-rhlc", Dependencies.ELASTICSEARCH_RHLC));
		config.add(Projects.ELASTICSEARCH,
				ProjectDependency.using("elasticsearch-java", Dependencies.ELASTICSEARCH_REST_CLIENT));

		config.add(Projects.LDAP, ProjectDependency.using("spring-ldap", Dependencies.SPRING_LDAP));

		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("awaitility.version", Dependencies.AWAITILITY));
		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("h2.version", Dependencies.H2));
		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("hsqldb.version", Dependencies.HSQLDB));
		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("db2.version", Dependencies.DB2_JDBC));
		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("mariadb-java-client.version", Dependencies.MARIADB_JDBC));
		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("mssql.version", Dependencies.MS_SQLSERVER_JDBC));
		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("mysql-connector-java.version", Dependencies.MYSQL_JDBC));
		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("mysql-connector-java.version", Dependencies.LEGACY_MYSQL_JDBC));
		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("postgresql.version", Dependencies.POSTGRES_JDBC));
		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("oracle.version", Dependencies.ORACLE_JDBC));

		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("r2dbc-postgresql.version", Dependencies.POSTGRES_R2DBC));
		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("r2dbc-h2.version", Dependencies.H2_R2DBC));
		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("r2dbc-mariadb.version", Dependencies.MARIADB_R2DBC));
		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("r2dbc-mssql.version", Dependencies.MS_SQLSERVER_R2DBC));
		config.add(Projects.RELATIONAL, ProjectDependency.managedProperty("r2dbc-mysql.version", Dependencies.MYSQL_R2DBC));
		config.add(Projects.RELATIONAL,
				ProjectDependency.managedProperty("oracle-r2dbc.version", Dependencies.ORACLE_R2DBC));
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

		boolean verifyUsage;

		Dependency dependency;

		public static ProjectDependency managedProperty(String pomProperty, Dependency dependency) {
			return new ProjectDependency(pomProperty, false, dependency);
		}

		public static ProjectDependency using(String pomProperty, Dependency dependency) {
			return new ProjectDependency(pomProperty, true, dependency);
		}
	}
}
