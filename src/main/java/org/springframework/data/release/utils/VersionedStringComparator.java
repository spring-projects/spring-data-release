/*
 * Copyright 2026-present the original author or authors.
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
package org.springframework.data.release.utils;

import java.util.Comparator;

import org.springframework.data.release.model.ArtifactVersion;

/**
 * A {@link Comparator} for strings that contain version numbers. Tokenizes the input strings and performs semantic
 * version comparison when both tokens at the same position match a semantic versioning pattern. Otherwise, performs
 * lexicographic comparison.
 * <p>
 * This comparator is particularly useful for sorting upgrade statements or dependency declarations where version
 * numbers need to be compared numerically rather than lexicographically.
 *
 * @author Mark Paluch
 */
public enum VersionedStringComparator implements Comparator<String> {

	INSTANCE;

	@Override
	public int compare(String s1, String s2) {

		if (s1 == null && s2 == null) {
			return 0;
		} else if (s1 == null) {
			return -1;
		} else if (s2 == null) {
			return 1;
		}

		String[] tokens1 = s1.split("\\s+");
		String[] tokens2 = s2.split("\\s+");

		int minLength = Math.min(tokens1.length, tokens2.length);

		for (int i = 0; i < minLength; i++) {

			String token1 = tokens1[i];
			String token2 = tokens2[i];

			// Both tokens are version numbers - perform semantic version comparison
			if (ArtifactVersion.isVersion(token1) && ArtifactVersion.isVersion(token2)) {

				ArtifactVersion v1 = ArtifactVersion.of(token1);
				ArtifactVersion v2 = ArtifactVersion.of(token2);

				int versionCompare = v1.compareTo(v2);
				if (versionCompare != 0) {
					return versionCompare;
				}
			} else {
				// Lexicographic comparison for non-version tokens
				int tokenCompare = token1.compareTo(token2);
				if (tokenCompare != 0) {
					return tokenCompare;
				}
			}
		}

		// If all compared tokens are equal, the shorter string comes first
		return s1.compareTo(s2);
	}

}
