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

import lombok.Value;

import org.springframework.util.ObjectUtils;

/**
 * Value object representing a OSS Sonatype staging repository.
 *
 * @author Mark Paluch
 */
@Value
public class StagingRepository {

	public static final StagingRepository EMPTY = new StagingRepository("", false);

	String id;
	boolean file;

	public static StagingRepository of(String id) {
		return new StagingRepository(id, false);
	}

	public static StagingRepository ofFile(String id) {
		return new StagingRepository(id, true);
	}

	public boolean isEmpty() {
		return ObjectUtils.isEmpty(id);
	}

	public boolean isPresent() {
		return !isEmpty();
	}

	public boolean isRemote() {
		return !isEmpty() && !isFile();
	}

	@Override
	public String toString() {

		if (isPresent()) {
			return (isFile() ? "file:" : "remote:") + id;
		}

		return "(empty)";
	}
}
