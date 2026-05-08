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
package org.springframework.data.release.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import org.springframework.util.Assert;

/**
 * A hotfix (the fourth digit of a version number).
 *
 * @author Mark Paluch
 */
@EqualsAndHashCode
@ToString
public class Hotfix {

	private static final Hotfix NONE = new Hotfix(-1);

	private final int hotfix;

	private Hotfix(int hotfix) {
		this.hotfix = hotfix;
	}

	/**
	 * Not a hotfix.
	 */
	public static Hotfix none() {
		return NONE;
	}

	/**
	 * Creates a new {@link Hotfix} instance.
	 */
	public static Hotfix of(int hotfix) {
		return new Hotfix(hotfix);
	}

	/**
	 * Increment the hotfix by one.
	 *
	 * @return the new hotfix.
	 */
	public Hotfix increment() {
		Assert.state(hotfix != -1, "Cannot increment a non-existing hotfix!");
		return new Hotfix(hotfix + 1);
	}

	public boolean isHotfix() {
		return hotfix != -1;
	}

	public int getValue() {
		return hotfix;
	}

}
