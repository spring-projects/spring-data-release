/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.release.deployment;

import org.springframework.util.ObjectUtils;

/**
 * Value object representing a OSS Sonatype staging repository.
 *
 * @author Mark Paluch
 */
public class StagingRepository {

	public static final StagingRepository EMPTY = StagingRepository.of("");

	private final String id;

	protected StagingRepository(String id) {
		this.id = id;
	}

	public static StagingRepository of(String id) {
		return new StagingRepository(id);
	}

	public boolean isEmpty() {
		return ObjectUtils.isEmpty(id);
	}

	public boolean isPresent() {
		return !isEmpty();
	}

	@Override
	public String toString() {
		if (isPresent()) {
			return id;
		}

		return "(empty)";
	}

	public String getId() {
		return this.id;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof StagingRepository)) {
			return false;
		}
		StagingRepository that = (StagingRepository) o;
		return ObjectUtils.nullSafeEquals(id, that.id);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(id);
	}

}
