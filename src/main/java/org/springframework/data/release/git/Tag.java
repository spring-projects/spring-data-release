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
package org.springframework.data.release.git;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.lang.Nullable;

/**
 * Value object to represent an SCM tag.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@EqualsAndHashCode
public class Tag implements Comparable<Tag> {

	@Getter
	private final String name;
	@Getter
	private final LocalDateTime creationDate;

	private final @Nullable ArtifactVersion artifactVersion;

	private Tag(String name, LocalDateTime creationDate, @Nullable ArtifactVersion artifactVersion) {
		this.name = name;
		this.creationDate = creationDate;
		this.artifactVersion = artifactVersion;
	}

	private Tag(String name, LocalDateTime creationDate) {
		this.name = name;
		this.creationDate = creationDate;

		ArtifactVersion artifactVersion;
		try {
			artifactVersion = ArtifactVersion.of(getVersionSource());
		} catch (Exception e) {
			artifactVersion = null;
		}

		this.artifactVersion = artifactVersion;
	}

	public static Tag of(String source) {
		return of(source, LocalDateTime.now());
	}

	public static Tag of(String source, LocalDateTime creationDate) {

		int slashIndex = source.lastIndexOf('/');

		return new Tag(source.substring(slashIndex == -1 ? 0 : slashIndex + 1), creationDate);
	}

	/**
	 * Returns the part of the name of the tag that is suitable to derive a version from the tag. Will transparently strip
	 * a {@code v} prefix from the name.
	 *
	 * @return
	 */
	private String getVersionSource() {
		return name.startsWith("v") ? name.substring(1) : name;
	}

	public boolean isVersionTag() {
		return getArtifactVersion().isPresent();
	}

	public Optional<ArtifactVersion> getArtifactVersion() {
		return Optional.ofNullable(artifactVersion);
	}

	public ArtifactVersion getRequiredArtifactVersion() {

		if (artifactVersion == null) {
			throw new IllegalStateException("Artifact version not set for tag '%s'".formatted(name));
		}

		return this.artifactVersion;
	}

	/**
	 * Creates a new {@link Tag} for the given {@link ArtifactVersion} based on the format of the current one.
	 *
	 * @param version
	 * @return
	 */
	public Tag createNew(ArtifactVersion version) {
		return new Tag(name.startsWith("v") ? "v".concat(version.toString()) : version.toString(), LocalDateTime.now(),
				artifactVersion);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return name;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Tag that) {

		// Prefer artifact versions but fall back to name comparison

		return getArtifactVersion().map(left -> that.getArtifactVersion().map(right -> left.compareTo(right)).//
				orElse(name.compareTo(that.name))).orElse(name.compareTo(that.name));
	}
}
