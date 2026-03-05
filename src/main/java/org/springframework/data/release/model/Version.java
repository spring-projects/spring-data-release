package org.springframework.data.release.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Value object to represent a Version consisting of major, minor and bugfix part.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class Version implements Comparable<Version> {

	private final BigDecimal[] parts;

	/**
	 * Creates a new {@link Version} from the given integer values. At least one value has to be given but a maximum of 4.
	 *
	 * @param parts must not be {@literal null} or empty.
	 */
	private Version(int... parts) {
		this(Arrays.stream(parts).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new));
	}

	/**
	 * Creates a new {@link Version} from the given integer values. At least one value has to be given but a maximum of 4.
	 *
	 * @param parts must not be {@literal null} or empty.
	 */
	private Version(int major, int minor, int bugfix) {
		this(BigDecimal.valueOf(major), BigDecimal.valueOf(minor), BigDecimal.valueOf(bugfix));
	}

	/**
	 * Creates a new {@link Version} from the given integer values. At least one value has to be given but a maximum of 4.
	 *
	 * @param parts must not be {@literal null} or empty.
	 */
	private Version(BigDecimal... parts) {

		Assert.notNull(parts, "Parts must not be null!");
		Assert.isTrue(parts.length > 0, "We need at least 1 part!");

		this.parts = parts;

		Assert.isTrue(getMajor() >= 0, "Major version must be greater or equal zero!");
		Assert.isTrue(getMinor() >= 0, "Minor version must be greater or equal zero!");
	}

	public static Version of(int... parts) {
		return new Version(Arrays.stream(parts).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new));
	}

	/**
	 * Parses the given string representation of a version into a {@link Version} object.
	 *
	 * @param version must not be {@literal null} or empty.
	 * @return
	 */
	public static Version parse(String version) {

		Assert.hasText(version, "Version must not be null or empty!");

		String[] parts = version.trim().split("\\.");
		BigDecimal[] intParts = new BigDecimal[parts.length];

		for (int i = 0; i < parts.length; i++) {
			intParts[i] = new BigDecimal(parts[i]);
		}

		return new Version(intParts);
	}

	public int getComponents() {
		return parts.length;
	}

	public int getMajor() {
		return parts.length > 0 ? parts[0].intValue() : 0;
	}

	public int getMinor() {
		return parts.length > 1 ? parts[1].intValue() : 0;
	}

	public int getBugfix() {
		return parts.length > 2 ? parts[2].intValue() : 0;
	}

	public int getBuild() {
		return parts.length > 3 ? parts[3].intValue() : 0;
	}

	/**
	 * Returns whether the current {@link Version} is greater (newer) than the given one.
	 *
	 * @param version
	 * @return
	 */
	public boolean isGreaterThan(Version version) {
		return compareTo(version) > 0;
	}

	/**
	 * Returns whether the current {@link Version} is greater (newer) or the same as the given one.
	 *
	 * @param version
	 * @return
	 */
	public boolean isGreaterThanOrEqualTo(Version version) {
		return compareTo(version) >= 0;
	}

	/**
	 * Returns whether the current {@link Version} is the same as the given one.
	 *
	 * @param version
	 * @return
	 */
	public boolean is(Version version) {
		return equals(version);
	}

	/**
	 * Returns whether the current {@link Version} has the same major and minor version as the given one.
	 *
	 * @param other
	 * @return
	 */
	public boolean hasSameMajorMinor(Version other) {
		return getMajor() == other.getMajor() && getMinor() == other.getMinor();
	}

	/**
	 * Returns whether the current {@link Version} is less (older) than the given one.
	 *
	 * @param version
	 * @return
	 */
	public boolean isLessThan(Version version) {
		return compareTo(version) < 0;
	}

	/**
	 * Returns whether the current {@link Version} is less (older) or equal to the current one.
	 *
	 * @param version
	 * @return
	 */
	public boolean isLessThanOrEqualTo(Version version) {
		return compareTo(version) <= 0;
	}

	public Version nextMajor() {
		return new Version(getMajor() + 1, 0, 0);
	}

	public Version nextMinor() {
		return new Version(getMajor(), getMinor() + 1, 0);
	}

	public Version nextBugfix() {
		return new Version(getMajor(), getMinor(), getBugfix() + 1);
	}

	public Version withBugfix(BigDecimal bugfix) {
		return new Version(getMajor(), getMinor(), bugfix.intValueExact());
	}

	public Version withBugfix(int bugfix) {
		return new Version(getMajor(), getMinor(), bugfix);
	}

	public String toMajorMinor() {
		return String.format("%s.%s", getMajor(), getMinor());
	}

	public String toMajorMinorBugfix() {
		return String.format("%s.%s.%s", getMajor(), getMinor(), getBugfix());
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Version that) {

		if (that == null) {
			return 1;
		}

		int maxLength = Math.max(this.parts.length, that.parts.length);

		for (int i = 0; i < maxLength; i++) {

			BigDecimal thisPart = i < this.parts.length ? this.parts[i] : BigDecimal.ZERO;
			BigDecimal thatPart = i < that.parts.length ? that.parts[i] : BigDecimal.ZERO;

			int comparison = thisPart.compareTo(thatPart);
			if (comparison != 0) {
				return comparison;
			}
		}

		return 0;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Version)) {
			return false;
		}

		return toString().equals(obj.toString());
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {

		return Arrays.hashCode(parts);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		List<BigDecimal> digits = new ArrayList<>(Arrays.asList(parts));

		while (digits.size() < 1) {
			digits.add(BigDecimal.ZERO);
		}

		return StringUtils.collectionToDelimitedString(digits, ".");
	}

}
