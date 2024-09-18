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

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.ReflectionUtils;

/**
 * @author Mark Paluch
 * @author Jens Schauder
 */
public class Dependencies {

	static final List<Dependency> dependencies = new ArrayList<>();

	public static final Dependency APT = Dependency.of("APT", "com.mysema.maven:apt-maven-plugin");

	public static final Dependency ASPECTJ = Dependency.of("AspectJ", "org.aspectj:aspectjrt");

	public static final Dependency ASSERTJ = Dependency.of("AssertJ", "org.assertj:assertj-core");

	public static final Dependency JACKSON = Dependency.of("Jackson", "com.fasterxml.jackson:jackson-bom");

	public static final Dependency JACOCO = Dependency.of("Jacoco", "org.jacoco:jacoco");

	public static final Dependency JODA_TIME = Dependency.of("Joda Time", "joda-time:joda-time");

	public static final Dependency JUNIT5 = Dependency.of("JUnit", "org.junit:junit-bom");

	public static final Dependency JUNIT4 = Dependency.of("JUnit", "junit:junit");

	public static final Dependency KOTLIN = Dependency.of("Kotlin", "org.jetbrains.kotlin:kotlin-bom");

	public static final Dependency KOTLIN_COROUTINES = Dependency.of("Kotlin Coroutines",
			"org.jetbrains.kotlinx:kotlinx-coroutines-bom");

	public static final Dependency MICROMETER = Dependency.of("Micrometer", "io.micrometer:micrometer-bom");

	public static final Dependency MICROMETER_TRACING = Dependency.of("Micrometer Tracing",
			"io.micrometer:micrometer-tracing-bom");

	public static final Dependency MOCKITO = Dependency.of("Mockito", "org.mockito:mockito-core");

	public static final Dependency MOCKK = Dependency.of("Mockk", "io.mockk:mockk");

	public static final Dependency QUERYDSL = Dependency.of("Querydsl", "com.querydsl:querydsl-bom");

	public static final Dependency RXJAVA1 = Dependency.of("RxJava", "io.reactivex:rxjava");

	public static final Dependency RXJAVA2 = Dependency.of("RxJava", "io.reactivex.rxjava2:rxjava");

	public static final Dependency RXJAVA3 = Dependency.of("RxJava", "io.reactivex.rxjava3:rxjava");

	public static final Dependency RXJAVA_RS = Dependency.of("RxJava Reactive Streams",
			"io.reactivex:rxjava-reactive-streams");

	public static final Dependency SPRING_HATEOAS = Dependency.of("Spring Hateoas",
			"org.springframework.hateoas:spring-hateoas");

	public static final Dependency SPRING_PLUGIN = Dependency.of("Spring Plugin",
			"org.springframework.plugin:spring-plugin");

	public static final Dependency TESTCONTAINERS = Dependency.of("Testcontainers", "org.testcontainers:testcontainers");

	public static final Dependency THREE_TEN_BP = Dependency.of("ThreeTenBp", "org.threeten:threetenbp");

	public static final Dependency OPEN_WEB_BEANS = Dependency.of("OpenWebBeans", "org.apache.openwebbeans:openwebbeans");

	public static final Dependency SMALLRYE_MUTINY = Dependency.of("Smallrye Mutiny", "io.smallrye.reactive:mutiny");

	public static final Dependency VAVR = Dependency.of("Vavr", "io.vavr:vavr").excludeVersionStartingWith("1.0.0-alpha");

	public static final Dependency XML_BEAM = Dependency.of("XMLBeam", "org.xmlbeam:xmlprojector");

	public static final Dependency HIBERNATE = Dependency.of("Hibernate", "org.hibernate.orm:hibernate-core");

	public static final Dependency ECLIPSELINK = Dependency.of("Eclipselink",
			"org.eclipse.persistence:org.eclipse.persistence.jpa");

	public static final Dependency MONGODB_CORE = Dependency.of("MongoDB", "org.mongodb:mongodb-driver-core");

	public static final Dependency MONGODB_LEGACY = Dependency.of("MongoDB", "org.mongodb:mongo-java-driver");

	public static final Dependency MONGODB_SYNC = Dependency.of("MongoDB", "org.mongodb:mongodb-driver-sync");

	public static final Dependency MONGODB_ASYNC = Dependency.of("MongoDB", "org.mongodb:mongodb-driver-async");

	public static final Dependency MONGODB_RS = Dependency.of("MongoDB Reactive Streams",
			"org.mongodb:mongodb-driver-reactivestreams");

	public static final Dependency LETTUCE = Dependency.of("Lettuce", "io.lettuce:lettuce-core");

	public static final Dependency JEDIS = Dependency.of("Jedis", "redis.clients:jedis");

	public static final Dependency JMOLECULES = Dependency.of("JMolecules", "org.jmolecules:jmolecules");

	public static final Dependency JMOLECULES_INTEGRATION = Dependency.of("JMolecules",
			"org.jmolecules.integrations:jmolecules-spring");

	public static final Dependency CASSANDRA_DRIVER3 = Dependency.of("Cassandra Driver",
			"com.datastax.cassandra:cassandra-driver-core");

	public static final Dependency CASSANDRA_DRIVER4 = Dependency.of("Cassandra Driver",
			"com.datastax.oss:java-driver-bom");

	public static final Dependency NEO4J_OGM = Dependency.of("Neo4j OGM", "org.neo4j:neo4j-ogm-api");

	public static final Dependency NEO4J_DRIVER = Dependency.of("Neo4j Driver", "org.neo4j.driver:neo4j-java-driver");

	public static final Dependency COUCHBASE = Dependency.of("Couchbase Client", "com.couchbase.client:java-client");

	public static final Dependency ELASTICSEARCH_RHLC = Dependency.of("Elasticsearch",
			"org.elasticsearch.client:elasticsearch-rest-high-level-client");

	public static final Dependency ELASTICSEARCH_REST_CLIENT = Dependency.of("Elasticsearch REST Client",
			"org.elasticsearch.client:elasticsearch-rest-client");

	public static final Dependency SPRING_LDAP = Dependency.of("Spring LDAP",
			"org.springframework.ldap:spring-ldap-core");

	public static final Dependency MAVEN = Dependency.of("Maven Wrapper", "org.apache.maven:apache-maven");

	public static final Dependency H2 = Dependency.of("H2 Database", "com.h2database:h2");
	public static final Dependency H2_R2DBC = Dependency.of("H2 R2DBC Driver", "io.r2dbc:r2dbc-h2");
	public static final Dependency HSQLDB = Dependency.of("HSQL Database", "org.hsqldb:hsqldb");
	public static final Dependency DB2_JDBC = Dependency.of("DB2 JDBC Driver", "com.ibm.db2:jcc");
	public static final Dependency MARIADB_JDBC = Dependency.of("MariaDB JDBC Driver", "org.mariadb.jdbc:mariadb-java-client");
	public static final Dependency MARIADB_R2DBC = Dependency.of("MariaDB R2DBC Driver", "rg.mariadb:r2dbc-mariadb");
	public static final Dependency MS_SQLSERVER_JDBC = Dependency.of("Microsoft SqlServer JDBC Driver", "com.microsoft.sqlserver:mssql-jdbc");
	public static final Dependency MS_SQLSERVER_R2DBC = Dependency.of("Microsoft SqlServer R2DBC Driver", "io.r2dbc:r2dbc-mssql");
	public static final Dependency MYSQL_JDBC = Dependency.of("MySql JDBC Driver", "mysql:mysql-connector-java");
	public static final Dependency MYSQL_R2DBC = Dependency.of("MySql R2DBC Driver", "io.asyncer:r2dbc-mysql");
	public static final Dependency POSTGRES_JDBC = Dependency.of("Postgres JDBC Driver", "org.postgresql:postgresql");
	public static final Dependency POSTGRES_R2DBC = Dependency.of("Postgres R2DBC Driver", "org.postgresql:r2dbc-postgresql");
	public static final Dependency ORACLE_JDBC = Dependency.of("Oracle JDBC Driver", "com.oracle.database.jdbc:ojdbc11");
	public static final Dependency ORACLE_R2DBC = Dependency.of("Oracle R2DBC Driver", "com.oracle.database.r2dbc:oracle-r2dbc");

	static {

		ReflectionUtils.doWithFields(Dependencies.class, field -> {

			// ignore dependencies constant
			if (field.getName().toLowerCase().equals(field.getName())) {
				return;
			}

			dependencies.add((Dependency) ReflectionUtils.getField(field, null));
		});
	}

	public static Dependency getRequiredByName(String name) {

		return dependencies.stream().filter(it -> it.getName().equals(name)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(String.format("No such dependency: %s", name)));
	}

	public static Dependency getRequiredDepependency(String groupId, String artifactId) {

		return dependencies.stream().filter(it -> it.getGroupId().equals(groupId) && it.getArtifactId().equals(artifactId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(String.format("No such dependency: %s", artifactId)));
	}

}
