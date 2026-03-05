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
import lombok.With;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.util.ObjectUtils;

/**
 * Value object representing a dependency version. The primary identifier is {@link #identifier} that corresponds with
 * the actual version identifier. Version identifiers are attempted to be parsed into either a version following Spring
 * Version or SemVer rules ({@code 1.2.3.RELEASE}, {@code 1.2.3-rc1}) or train name with counter rules
 * ({@code Foo-RELEASE}, {@code Foo-SR1}).
 *
 * @author Mark Paluch
 */
@Value
class DependencyVersion implements Comparable<DependencyVersion> {

	private static Pattern VERSION = Pattern.compile("((?>(?>\\d+)[\\.]?)+)((?>-)?[a-zA-Z]+)?(\\d+)?");
	private static Pattern NAME_VERSION = Pattern.compile("([A-Za-z]+)-(RELEASE|SR(\\d+)|SNAPSHOT|BUILD-SNAPSHOT)");

	private static Comparator<DependencyVersion> TRAIN_VERSION_COMPARATOR = Comparator
			.comparing(DependencyVersion::getTrainName).thenComparing(DependencyVersion::getVersion);

	String identifier;
	String trainName;
	ArtifactVersion version;
	@With LocalDateTime createdAt;

	public static DependencyVersion of(String identifier) {

		Matcher bomMatcher = NAME_VERSION.matcher(identifier);

		if (bomMatcher.find()) {

			String group = bomMatcher.group(1);
			String suffix = bomMatcher.group(2);
			String newIdentifier = ((int) group.charAt(0)) + ".0.0-" + suffix;
			return new DependencyVersion(identifier, group, ArtifactVersion.of(newIdentifier), null);
		}

		return new DependencyVersion(identifier, null, ArtifactVersion.of(identifier), null);
	}

	public boolean isNewer(DependencyVersion other) {
		return this.compareTo(other) > 0;
	}

	public boolean hasSameMajorMinor(DependencyVersion other) {
		return version.getVersion().hasSameMajorMinor(other.getVersion().getVersion());
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof DependencyVersion that)) {
			return false;
		}

		return compareTo(that) == 0;
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(identifier, trainName, version);
	}

	@Override
	public int compareTo(DependencyVersion o) {

		if (trainName != null && o.trainName != null) {
			return TRAIN_VERSION_COMPARATOR.compare(this, o);
		}

		if (trainName != null) {
			return -1;
		}

		if (o.trainName != null) {
			return 1;
		}

		if (version != null && o.version != null) {
			return version.compareTo(o.version);
		}

		return identifier.compareTo(o.identifier);
	}

	@Override
	public String toString() {
		return identifier;
	}

	public boolean hasPreReleaseModifier() {
		return !version.isReleaseVersion() && !version.isBugFixVersion();
	}

}
