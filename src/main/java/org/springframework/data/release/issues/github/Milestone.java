/*
 * Copyright 2020-2022 the original author or authors.
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
package org.springframework.data.release.issues.github;

import lombok.Value;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.release.model.ModuleIteration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Value
@JsonInclude(content = JsonInclude.Include.NON_NULL)
public class Milestone {

	Long number;
	String title, description, state;
	@JsonProperty("due_on") Instant dueOn;

	public static Milestone of(String title, String description) {
		return new Milestone(null, title, description, null, null);
	}

	public boolean matches(ModuleIteration moduleIteration) {

		return moduleIteration.getSupportedProject().getProject().isUseShortVersionMilestones()
				? title.equals(moduleIteration.getReleaseVersionString())
				: title.contains(moduleIteration.getShortVersionString());
	}

	@JsonIgnore
	public boolean isOpen() {
		return "open".equals(state);
	}

	public Milestone markReleased() {
		return new Milestone(number, null, null, "closed", null);
	}

	public boolean isNearFuture() {
		return getDueOn() != null && getDueOn().isAfter(Instant.now())
				&& getDueOn().isBefore(Instant.now().plus(Duration.ofDays(30)));

	}
}
