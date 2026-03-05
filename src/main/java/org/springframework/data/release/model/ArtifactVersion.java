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

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Value object to represent version of a particular artifact.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class ArtifactVersion implements Comparable<ArtifactVersion> {

	private static final Pattern PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)+)(\\.((SR\\d+)|(RC\\d+)|(M\\d+)|(BUILD-SNAPSHOT)|(RELEASE)))");

	private static final Pattern VERSION_FALLBACK = Pattern.compile("((\\d+)(\\.\\d+)+)([.-])?(.*)");

	private static final Pattern MODIFIER_PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)+)(-((RC\\d+)|(M\\d+)|(SNAPSHOT)))?");

	private static final String RELEASE_SUFFIX = "RELEASE";
	private static final String MILESTONE_SUFFIX = "M\\d+";
	private static final String RC_SUFFIX = "RC\\d+";
	private static final String BUILD_SNAPSHOT_SUFFIX = "BUILD-SNAPSHOT";
	private static final String SNAPSHOT_MODIFIER = "SNAPSHOT";

	private static final Pattern MILESTONE_OR_RC_PATTERN = Pattern.compile("(SR|RC|M)(\\d+)");

	private static final Pattern SEMVER_SUFFIX = Pattern.compile("([a-zA-Z]+)([.-])?(\\d*)");

	private static final Pattern SEMVER_PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)+)(([.-])(" + SEMVER_SUFFIX.pattern() + "))?");

	private static final String VALID_SUFFIX = String.format("%s|%s|%s|%s|-%s|-%s|-%s", RELEASE_SUFFIX, MILESTONE_SUFFIX,
			RC_SUFFIX, BUILD_SNAPSHOT_SUFFIX, RELEASE_SUFFIX, MILESTONE_SUFFIX, SNAPSHOT_MODIFIER);

	private final @Getter Version version;
	private final @With boolean modifierFormat;
	private final @With boolean skipSeparator;
	private final @Getter Suffix suffix;

	/**
	 * Creates a new {@link ArtifactVersion} from the given logical {@link Version}.
	 *
	 * @param version must not be {@literal null}.
	 * @param modifierFormat
	 */
	private ArtifactVersion(Version version, boolean modifierFormat) {
		this(version, modifierFormat, modifierFormat ? Release.INSTANCE : Release.RELEASE);
	}

	/**
	 * Creates a new {@link ArtifactVersion} from the given logical {@link Version}.
	 *
	 * @param version must not be {@literal null}.
	 * @param modifierFormat
	 */
	private ArtifactVersion(Version version, boolean modifierFormat, Suffix suffix) {

		Assert.notNull(version, "Version must not be null!");
		Assert.notNull(suffix, "Suffix must not be null!");

		this.version = version;
		this.modifierFormat = modifierFormat;
		this.skipSeparator = false;
		this.suffix = suffix;
	}

	/**
	 * Creates a new {@link ArtifactVersion} from the given logical {@link Version}.
	 *
	 * @param version must not be {@literal null}.
	 * @param modifierFormat
	 */
	private ArtifactVersion(Version version, boolean modifierFormat, boolean skipSeparator, Suffix suffix) {

		Assert.notNull(version, "Version must not be null!");
		Assert.notNull(suffix, "Suffix must not be null!");

		this.version = version;
		this.modifierFormat = modifierFormat;
		this.skipSeparator = skipSeparator;
		this.suffix = suffix;
	}

	public static ArtifactVersion of(Version version) {
		return new ArtifactVersion(version, false, Release.RELEASE);
	}

	public static ArtifactVersion of(Version version, boolean useModifierFormat) {
		return new ArtifactVersion(version, useModifierFormat, useModifierFormat ? Release.INSTANCE : Release.RELEASE);
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

			Assert.isTrue(suffix.matches(VALID_SUFFIX), String.format("Invalid version suffix: %s!", source));

			return new ArtifactVersion(version, false, Suffix.parse(suffix));
		}

		matcher = MODIFIER_PATTERN.matcher(source);

		if (matcher.matches()) {

			Version version = Version.parse(matcher.group(1));
			String suffix = matcher.group(5);

			return new ArtifactVersion(version, true, Suffix.parse(suffix));
		}

		matcher = SEMVER_PATTERN.matcher(source);

		if (matcher.matches()) {

			Version version = Version.parse(matcher.group(1));
			String modifierdelimiter = matcher.group(5);
			String suffix = matcher.group(6);

			return new ArtifactVersion(version, modifierdelimiter.equals("-"), Suffix.parse(suffix));
		}

		matcher = VERSION_FALLBACK.matcher(source);

		if (matcher.matches()) {

			Version version = Version.parse(matcher.group(1));
			String modifierdelimiter = matcher.group(4);
			String suffix = matcher.group(5);

			return new ArtifactVersion(version, "-".equals(modifierdelimiter),
					!("-".equals(modifierdelimiter) || ".".equals(modifierdelimiter)), Suffix.parse(suffix));
		}

		throw new IllegalArgumentException(
				String.format("Version %s does not match <version>.<modifier> nor <version>-<modifier> pattern", source));
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
			return new ArtifactVersion(version, modifierVersionFormat);
		}

		if (iteration.isServiceIteration()) {
			Version bugfixVersion = version.withBugfix(iteration.getBugfixValue());
			return of(bugfixVersion, modifierVersionFormat);
		}

		return new ArtifactVersion(version, modifierVersionFormat, Suffix.parse(iteration.getName()));
	}

	/**
	 * Returns whether the given source represents a valid version.
	 *
	 * @param source
	 * @return
	 */
	public static boolean isVersion(String source) {
		try {
			of(source);
			return true;
		} catch (IllegalArgumentException o_O) {
			return false;
		}
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
		return new ArtifactVersion(version, modifierFormat);
	}

	/**
	 * Returns the snapshot version of the current one.
	 *
	 * @return
	 */
	public ArtifactVersion getSnapshotVersion() {
		return snapshotOf(version);
	}

	/**
	 * Returns whether the version is a release version.
	 *
	 * @return
	 */
	public boolean isReleaseVersion() {
		return suffix instanceof Release;
	}

	/**
	 * Returns whether the version is a milestone version.
	 *
	 * @return
	 */
	public boolean isMilestoneVersion() {

		if (suffix instanceof SemVerSuffix sv && sv.isMilestone()) {
			return true;
		}

		String canonical = suffix.canonical().toLowerCase();
		return canonical.contains("alpha") || canonical.contains("beta");
	}

	/**
	 * Returns whether the version is a RC version.
	 *
	 * @return
	 */
	public boolean isReleaseCandidateVersion() {

		if (suffix instanceof SemVerSuffix sv && sv.isReleaseCandidate()) {
			return true;
		}

		return suffix.canonical().toLowerCase().contains("rc");
	}

	public String getSuffix() {
		return suffix.canonical();
	}

	public int getLevel() {

		if (suffix instanceof SemVerSuffix sv) {
			return sv.counter();
		}

		if (isBugFixVersion()) {
			return version.getBugfix();
		}

		throw new IllegalStateException("Not a M/RC/SR release");

	}

	public boolean isSnapshotVersion() {
		return suffix instanceof Snapshot;
	}

	public boolean isBugFixVersion() {
		return isReleaseVersion() && version.getBugfix() != 0;
	}

	/**
	 * Returns the next development version to be used for the current release version, which means next minor for GA
	 * versions and next bug fix for service releases. Will return the current version as snapshot otherwise.
	 *
	 * @return
	 */
	public ArtifactVersion getNextDevelopmentVersion() {

		if (isReleaseVersion() || isBugFixVersion()) {

			boolean isGaVersion = version.withBugfix(0).equals(version);
			Version nextVersion = isGaVersion ? version.nextMinor() : version.nextBugfix();

			return snapshotOf(nextVersion);
		}

		return isSnapshotVersion() ? this : snapshotOf(version);
	}

	/**
	 * Returns the next bug fix version for the current version if it's a release version or the snapshot version of the
	 * current one otherwise.
	 *
	 * @return
	 */
	public ArtifactVersion getNextBugfixVersion() {

		if (isReleaseVersion()) {
			return snapshotOf(version.nextBugfix());
		}

		return isSnapshotVersion() ? this : snapshotOf(version);
	}

	/**
	 * @return the next minor version retaining the modifier and snapshot suffix.
	 */
	public ArtifactVersion getNextMinorVersion() {
		return versionOf(version.nextMinor());
	}

	public String getReleaseTrainSuffix() {

		if (isSnapshotVersion() || isMilestoneVersion() || isReleaseCandidateVersion()) {
			return suffix.toString();
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

		if (skipSeparator) {
			return String.format("%s%s", version.toString(), suffix);
		}

		if (modifierFormat) {

			if (isSnapshotVersion() || isMilestoneVersion() || isReleaseCandidateVersion() || suffix instanceof Generic) {
				return String.format("%s-%s", version.toString(), suffix);
			}

			return version.toString();
		}

		return String.format("%s.%s", version.toString(), suffix);
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
		return modifierFormat ? SNAPSHOT_MODIFIER : BUILD_SNAPSHOT_SUFFIX;
	}

	public String getMajorMinor(boolean includeSuffix) {

		if (includeSuffix && isSnapshotVersion()) {
			return String.format("%s.%s-SNAPSHOT", version.getMajor(), version.getMinor());
		}

		return String.format("%s.%s", version.getMajor(), version.getMinor());
	}

	public String getGeneration() {
		return String.format("%s.x", getMajorMinor(false));
	}

	private ArtifactVersion snapshotOf(Version version) {
		return new ArtifactVersion(version, modifierFormat, Suffix.parse(getSnapshotSuffix()));
	}

	private ArtifactVersion versionOf(Version version) {
		return new ArtifactVersion(version, modifierFormat, suffix);
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
				&& ObjectUtils.nullSafeEquals(suffix.canonical(), other.suffix.canonical());
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, isReleaseVersion(), isSnapshotVersion(), isMilestoneVersion(),
				isReleaseCandidateVersion(), suffix.canonical());
	}

	/**
	 * Version suffix such as {@code SNAPSHOT}, {@code M1}, {@code RC1} or {@code RELEASE}.
	 */
	interface Suffix extends Comparable<Suffix> {

		/**
		 * Parse the suffix into a {@link Suffix} instance.
		 *
		 * @param suffix
		 * @return
		 */
		static Suffix parse(String suffix) {

			if (suffix == null || !StringUtils.hasText(suffix)) {
				return Release.INSTANCE;
			}

			if (suffix.equals("RELEASE")) {
				return Release.RELEASE;
			}

			if (suffix.equalsIgnoreCase("Final")) {
				return new Release(suffix);
			}

			if (suffix.equals(BUILD_SNAPSHOT_SUFFIX)) {
				return Snapshot.BUILD_SNAPSHOT;
			} else if (suffix.equals(SNAPSHOT_MODIFIER)) {
				return Snapshot.INSTANCE;
			}

			Matcher milestoneMatcher = MILESTONE_OR_RC_PATTERN.matcher(suffix);

			if (milestoneMatcher.find()) {
				String type = milestoneMatcher.group(1);
				int counter = Integer.parseInt(milestoneMatcher.group(2));
				return new SemVerSuffix(type, counter, "");
			}

			Matcher semVerPattern = SEMVER_SUFFIX.matcher(suffix);

			if (semVerPattern.matches()) {
				String type = semVerPattern.group(1);
				String separator = semVerPattern.group(2);
				String counterString = semVerPattern.group(3);
				int counter = StringUtils.hasText(counterString) ? Integer.parseInt(counterString) : -1;
				return new SemVerSuffix(type, counter, separator);
			}

			return new Generic(suffix);
		}

		/**
		 * Canonical suffix representation.
		 */
		String canonical();
	}

	/**
	 * Snapshot suffix such as {@code SNAPSHOT} or {@code BUILD-SNAPSHOT}.
	 *
	 * @param canonical
	 */
	record Snapshot(String canonical) implements Suffix {

		private final static Snapshot INSTANCE = new Snapshot("SNAPSHOT");

		private final static Snapshot BUILD_SNAPSHOT = new Snapshot("BUILD-SNAPSHOT");

		@Override
		public int compareTo(Suffix o) {
			return o instanceof Snapshot ? 0 : -1;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Release suffix (or no suffix at all).
	 *
	 * @param canonical
	 */
	record Release(String canonical) implements Suffix {

		private final static Release INSTANCE = new Release("");

		private final static Release RELEASE = new Release("RELEASE");

		@Override
		public int compareTo(Suffix o) {

			if (o instanceof SemVerSuffix sv) {
				if (sv.type.equals("SR")) {
					return -1;
				}
			}

			return o instanceof Release ? 0 : -1;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Generic suffix that doesn't fit into any of the other categories. Will be sorted alphabetically by canonical value.
	 *
	 * @param canonical
	 */
	record Generic(String canonical) implements Suffix {

		private static final Comparator<Suffix> COMPARATOR = Comparator.comparing(Suffix::canonical,
				String.CASE_INSENSITIVE_ORDER);

		@Override
		public int compareTo(Suffix o) {

			if (o instanceof Release) {
				return -1;
			}

			if (o instanceof Snapshot) {
				return 1;
			}

			return COMPARATOR.compare(this, o);
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Semantic versioning suffix such as {@code M1}, {@code RC1} or {@code SR1}.
	 *
	 * @param type
	 * @param counter
	 * @param dot
	 */
	record SemVerSuffix(String type, int counter, String separator) implements Suffix {

		private static final Comparator<SemVerSuffix> COMPARATOR = Comparator.comparing(SemVerSuffix::getCanonicalType)
				.thenComparingInt(SemVerSuffix::counter);

		private String getCanonicalType() {
			return type.toLowerCase(Locale.ROOT);
		}

		@Override
		public int compareTo(Suffix o) {

			if (o instanceof Snapshot) {
				return 1;
			}

			if (o instanceof Release) {
				return type.equals("SR") ? 1 : -1;
			}

			if (o instanceof SemVerSuffix other) {
				return COMPARATOR.compare(this, other);
			}

			return Generic.COMPARATOR.compare(this, o);
		}

		public boolean isMilestone() {
			return getCanonicalType().equals("m") || getCanonicalType().equals("alpha") || getCanonicalType().equals("beta");
		}

		public boolean isReleaseCandidate() {
			return getCanonicalType().equals("rc") || getCanonicalType().equals("cr");
		}

		@Override
		public String canonical() {

			if (counter == -1) {
				return type;
			}

			if (separator == null) {
				return "%s%d".formatted(type, counter);
			}
			return "%s%s%d".formatted(type, separator, counter);
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

}
