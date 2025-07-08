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

import lombok.Getter;
import lombok.With;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Value object to represent version of a particular artifact.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class ArtifactVersion implements Comparable<ArtifactVersion> {

	private static final Pattern PATTERN = Pattern
			.compile("(\\d+)\\.(\\d+)(\\.\\d+)?(\\.((SR\\d+)|(RC\\d+)|(M\\d+)|(BUILD-SNAPSHOT)|(RELEASE)))");

	private static final Pattern MODIFIER_PATTERN = Pattern
			.compile("((\\d+)\\.(\\d+)(\\.\\d+)?)(-((RC\\d+)|(M\\d+)|(SNAPSHOT)))?");

	private static final String RELEASE_SUFFIX = "RELEASE";
	private static final String MILESTONE_SUFFIX = "M\\d";
	private static final String RC_SUFFIX = "RC\\d";
	private static final String SNAPSHOT_SUFFIX = "BUILD-SNAPSHOT";
	private static final String SNAPSHOT_MODIFIER = "SNAPSHOT";

	private static final String VALID_SUFFIX = "%s|%s|%s|%s|-%s|-%s|-%s".formatted(RELEASE_SUFFIX, MILESTONE_SUFFIX,
			RC_SUFFIX, SNAPSHOT_SUFFIX, RELEASE_SUFFIX, MILESTONE_SUFFIX, SNAPSHOT_MODIFIER);

	private final @Getter Version version;
	private final @With boolean modifierFormat;
	private final @Getter String suffix;

	/**
	 * Creates a new {@link ArtifactVersion} from the given logical {@link Version}.
	 *
	 * @param version must not be {@literal null}.
	 * @param modifierFormat
	 * @param suffix must not be {@literal null} or empty.
	 */
	private ArtifactVersion(Version version, boolean modifierFormat, String suffix) {

		Assert.notNull(version, "Version must not be null!");
		Assert.hasText(suffix, "Suffix must not be null or empty!");

		this.version = version;
		this.modifierFormat = modifierFormat;
		this.suffix = suffix;
	}

	public static ArtifactVersion of(Version version) {
		return of(version, false);
	}

	public static ArtifactVersion of(Version version, boolean useModifierFormat) {
		return new ArtifactVersion(version, useModifierFormat, RELEASE_SUFFIX);
	}

	/**
	 * Parses the given {@link String} into an {@link ArtifactVersion}.
	 *
	 * @param source must not be {@literal null} or empty.
	 * @return
	 */
	public static ArtifactVersion of(String source) {

		Assert.hasText(source, "Version source must not be null or empty!");

		Matcher matcher = PATTERN.matcher(source);
		if (matcher.matches()) {

			int suffixStart = source.lastIndexOf('.');

			Version version = Version.parse(source.substring(0, suffixStart));
			String suffix = source.substring(suffixStart + 1);

			Assert.isTrue(suffix.matches(VALID_SUFFIX), "Invalid version suffix: %s!".formatted(source));

			return new ArtifactVersion(version, false, suffix);
		}

		matcher = MODIFIER_PATTERN.matcher(source);

		if (matcher.matches()) {

			Version version = Version.parse(matcher.group(1));
			String suffix = matcher.group(6);

			return new ArtifactVersion(version, true, suffix == null ? RELEASE_SUFFIX : suffix);
		}

		throw new IllegalArgumentException(
				"Version %s does not match <version>.<modifier> nor <version>-<modifier> pattern".formatted(source));
	}

	/**
	 * Creates a new {@link ArtifactVersion} from the given {@link IterationVersion}.
	 *
	 * @param iterationVersion must not be {@literal null}.
	 * @return
	 */
	public static ArtifactVersion of(IterationVersion iterationVersion) {

		Assert.notNull(iterationVersion, "IterationVersion must not be null!");

		Version version = iterationVersion.getVersion();
		Iteration iteration = iterationVersion.getIteration();
		boolean modifierVersionFormat = iterationVersion.usesModifierVersionFormat();

		if (iteration.isGAIteration()) {
			return new ArtifactVersion(version, modifierVersionFormat, RELEASE_SUFFIX);
		}

		if (iteration.isServiceIteration()) {
			Version bugfixVersion = version.withBugfix(iteration.getBugfixValue());
			return new ArtifactVersion(bugfixVersion, modifierVersionFormat, RELEASE_SUFFIX);
		}

		return new ArtifactVersion(version, modifierVersionFormat, iteration.getName());
	}

	public boolean isVersionWithin(Version version) {
		return this.version.toMajorMinorBugfix().startsWith(version.toString());
	}

	/**
	 * Returns the release version for the current one.
	 *
	 * @return
	 */
	public ArtifactVersion getReleaseVersion() {
		return new ArtifactVersion(version, modifierFormat, RELEASE_SUFFIX);
	}

	/**
	 * Returns the snapshot version of the current one.
	 *
	 * @return
	 */
	public ArtifactVersion getSnapshotVersion() {
		return new ArtifactVersion(version, modifierFormat, getSnapshotSuffix());
	}

	/**
	 * Returns whether the version is a release version.
	 *
	 * @return
	 */
	public boolean isReleaseVersion() {
		return suffix.equals("") || suffix.equals(RELEASE_SUFFIX);
	}

	/**
	 * Returns whether the version is a milestone version.
	 *
	 * @return
	 */
	public boolean isMilestoneVersion() {
		return suffix.matches(MILESTONE_SUFFIX);
	}

	/**
	 * Returns whether the version is a RC version.
	 *
	 * @return
	 */
	public boolean isReleaseCandidateVersion() {
		return suffix.matches(RC_SUFFIX);
	}

	public int getLevel() {

		if (isMilestoneVersion()) {
			Pattern pattern = Pattern.compile("M(\\d+)");
			Matcher matcher = pattern.matcher(suffix);
			matcher.find();
			return Integer.parseInt(matcher.group(1));
		}

		if (isReleaseCandidateVersion()) {
			Pattern pattern = Pattern.compile("RC(\\d+)");
			Matcher matcher = pattern.matcher(suffix);
			matcher.find();
			return Integer.parseInt(matcher.group(1));
		}

		if (isBugFixVersion()) {
			return version.getBugfix();
		}

		throw new IllegalStateException("Not a M/RC/SR release");

	}

	public boolean isSnapshotVersion() {
		return suffix.matches(SNAPSHOT_SUFFIX) || suffix.matches(SNAPSHOT_MODIFIER);
	}

	public boolean isBugFixVersion() {
		return isReleaseVersion() && !version.toMajorMinorBugfix().endsWith(".0");
	}

	/**
	 * Returns the next development version to be used for the current release version, which means next minor for GA
	 * versions and next bug fix for service releases. Will return the current version as snapshot otherwise.
	 *
	 * @return
	 */
	public ArtifactVersion getNextDevelopmentVersion() {

		if (suffix.equals(RELEASE_SUFFIX)) {

			boolean isGaVersion = version.withBugfix(0).equals(version);
			Version nextVersion = isGaVersion ? version.nextMinor() : version.nextBugfix();

			return new ArtifactVersion(nextVersion, modifierFormat, getSnapshotSuffix());
		}

		return isSnapshotVersion() ? this : new ArtifactVersion(version, modifierFormat, getSnapshotSuffix());
	}

	/**
	 * Returns the next bug fix version for the current version if it's a release version or the snapshot version of the
	 * current one otherwise.
	 *
	 * @return
	 */
	public ArtifactVersion getNextBugfixVersion() {

		if (suffix.equals(RELEASE_SUFFIX)) {
			return new ArtifactVersion(version.nextBugfix(), modifierFormat, getSnapshotSuffix());
		}

		return isSnapshotVersion() ? this : new ArtifactVersion(version, modifierFormat, getSnapshotSuffix());
	}

	/**
	 * @return the next minor version retaining the modifier and snapshot suffix.
	 */
	public ArtifactVersion getNextMinorVersion() {
		return new ArtifactVersion(version.nextMinor(), modifierFormat, suffix);
	}

	public String getReleaseTrainSuffix() {

		if (isSnapshotVersion() || isMilestoneVersion() || isReleaseCandidateVersion()) {
			return suffix;
		}

		if (isBugFixVersion()) {
			return "SR" + version.getBugfix();
		}

		return "GA";
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(ArtifactVersion that) {

		int versionsEqual = this.version.compareTo(that.version);
		return versionsEqual != 0 ? versionsEqual : this.suffix.compareTo(that.suffix);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		if (modifierFormat) {

			if (isSnapshotVersion() || isMilestoneVersion() || isReleaseCandidateVersion()) {
				return "%s-%s".formatted(version.toMajorMinorBugfix(), suffix);
			}

			return version.toMajorMinorBugfix();
		}

		return "%s.%s".formatted(version.toMajorMinorBugfix(), suffix);
	}

	/**
	 * Returns the {@link String} of the plain version (read: x.y.z, omitting trailing bug fix zeros).
	 *
	 * @return
	 */
	public String toShortString() {
		return version.toString();
	}

	private String getSnapshotSuffix() {
		return modifierFormat ? SNAPSHOT_MODIFIER : SNAPSHOT_SUFFIX;
	}

	public String getMajorMinor(boolean includeSuffix) {

		if (includeSuffix && isSnapshotVersion()) {
			return "%s.%s-SNAPSHOT".formatted(version.getMajor(), version.getMinor());
		}

		return "%s.%s".formatted(version.getMajor(), version.getMinor());
	}

	public String getGeneration() {
		return "%s.x".formatted(getMajorMinor(false));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ArtifactVersion)) {
			return false;
		}
		ArtifactVersion other = (ArtifactVersion) o;
		return version.equals(other.version) && isReleaseVersion() == other.isReleaseVersion()
				&& isSnapshotVersion() == other.isSnapshotVersion() && isMilestoneVersion() == other.isMilestoneVersion()
				&& isReleaseCandidateVersion() == other.isReleaseCandidateVersion()
				&& ObjectUtils.nullSafeEquals(suffix, other.suffix);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, isReleaseVersion(), isSnapshotVersion(), isMilestoneVersion(),
				isReleaseCandidateVersion(), suffix);
	}

}
