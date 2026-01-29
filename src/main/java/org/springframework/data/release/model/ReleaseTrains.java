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

import static org.springframework.data.release.model.Projects.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.release.model.Train.DocumentationFormat;
import org.springframework.util.StringUtils;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class ReleaseTrains {

	public static final List<Train> TRAINS;
	public static final Train A, OCKHAM, VAUGHAN, W, X, Y, Z;

	static {

		OCKHAM = ockham().withIterations(Train.Iterations.DEFAULT).withCalver("2020.0") //
				.withSupportStatus(SupportStatus.EOL);

		Train PASCAL = OCKHAM.next("Pascal", Transition.MINOR) //
				.filterModules(module -> !module.getProject().equals(SOLR)) //
				.withCalver("2021.0") //
				.withSupportStatus(SupportStatus.EOL);

		Train Q = PASCAL.next("Q", Transition.MINOR) //
				.withCalver("2021.1") //
				.withSupportStatus(SupportStatus.EOL);

		Train RAJ = Q.next("Raj", Transition.MINOR) //
				.withCalver("2021.2") //
				.withSupportStatus(SupportStatus.COMMERCIAL);

		Train TURING = RAJ.next("Turing", Transition.MAJOR, //
				new Module(RELATIONAL, "3.0")) //
				.withCalver("2022.0") //
				.filterModules(module -> !module.getProject().equals(ENVERS))
				.filterModules(module -> !module.getProject().equals(GEODE))
				.filterModules(module -> !module.getProject().equals(R2DBC))
				.filterModules(module -> !module.getProject().equals(JDBC)) // filter "old" JDBC without R2DBC submodule
				.withSupportStatus(SupportStatus.COMMERCIAL);

		Train ULLMAN = TURING.next("Ullman", Transition.MINOR) //
				.withCalver("2023.0") //
				.withSupportStatus(SupportStatus.COMMERCIAL);

		VAUGHAN = ULLMAN.next("Vaughan", Transition.MINOR) //
				.withDocumentationFormat(DocumentationFormat.ANTORA) //
				.withCalver("2023.1") //
				.withSupportStatus(SupportStatus.COMMERCIAL);

		W = VAUGHAN.next("W", Transition.MINOR) //
				.withCalver("2024.0") //
				.withSupportStatus(SupportStatus.COMMERCIAL);

		X = W.next("X", Transition.MINOR) //
				.withCalver("2024.1").withSupportStatus(SupportStatus.COMMERCIAL);

		Y = X.next("Y", Transition.MINOR) //
				.withCalver("2025.0");

		Z = Y.next("Z", Transition.MAJOR) //
				.withCalver("2025.1");

		A = Z.next("A", Transition.MINOR) //
				.withCalver("2026.0");
		// Trains

		TRAINS = Arrays.asList(OCKHAM, RAJ, VAUGHAN, W, X, Y, Z, A);
	}

	private static Train ockham() {

		Module bom = BOM.toModule("2020.0.0");
		Module build = BUILD.toModule("2.4");
		Module commons = COMMONS.toModule("2.4");
		Module cassandra = CASSANDRA.toModule("3.1");
		Module envers = ENVERS.toModule("2.4");
		Module geode = GEODE.toModule("2.4");
		Module jpa = JPA.toModule("2.4");
		Module keyvalue = KEY_VALUE.toModule("2.4");
		Module ldap = LDAP.toModule("2.4");
		Module jdbc = JDBC.toModule("2.1");
		Module r2dbc = R2DBC.toModule("1.2");
		Module mongoDb = MONGO_DB.toModule("3.1");
		Module solr = SOLR.toModule("4.3");
		Module redis = REDIS.toModule("2.4");
		Module rest = REST.toModule("3.4");

		Module couchbase = COUCHBASE.toModule("4.1");
		Module elasticsearch = ELASTICSEARCH.toModule("4.1");
		Module neo4j = NEO4J.toModule("6.0");

		return new Train("Ockham", bom, build, cassandra, commons, envers, geode, jpa, jdbc, keyvalue, ldap, r2dbc, mongoDb,
				redis, rest, neo4j, solr, couchbase, elasticsearch);
	}

	public static Train getTrainByName(String name) {

		return TRAINS.stream() //
				.filter(it -> it.getName().equalsIgnoreCase(name)) //
				.findFirst() //
				.orElseThrow(() -> new IllegalArgumentException("Cannot resolve " + name + " to a Release Train"));
	}

	public static Train getTrainByCalver(Version calver) {

		return TRAINS.stream() //
				.filter(Train::usesCalver)
				.filter(it -> it.getCalver().getMajor() == calver.getMajor() && it.getCalver().getMinor() == calver.getMinor()) //
				.findFirst() //
				.orElse(null);
	}

	public static Project getProjectByName(String name) {

		return PROJECTS.stream() //
				.filter(it -> it.getName().equalsIgnoreCase(name)) //
				.findFirst() //
				.orElse(null);
	}

	public static List<Train> getTrains(String trainNamesOrLastNumber, int defaultLastTrains) {

		if (StringUtils.hasText(trainNamesOrLastNumber)) {

			try {
				List<Train> trains = ReleaseTrains.latest(Integer.parseInt(trainNamesOrLastNumber));
				Collections.reverse(trains);
				return trains;
			} catch (NumberFormatException e) {
				return Stream.of(trainNamesOrLastNumber.split(",")).map(String::trim) //
						.map(ReleaseTrains::getTrainByName).collect(Collectors.toList());
			}
		} else {
			return ReleaseTrains.latest(defaultLastTrains);
		}
	}

	public static List<Train> trains() {
		return TRAINS;
	}

	public static Train latest() {
		return TRAINS.get(TRAINS.size() - 1);
	}

	public static List<Train> latest(int count) {
		return TRAINS.subList(TRAINS.size() - count, TRAINS.size());
	}
}
