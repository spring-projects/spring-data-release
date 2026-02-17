/*
 * Copyright 2024 the original author or authors.
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

import lombok.Value;
import lombok.With;

/**
 * @author Oliver Drotbohm
 */
@Value(staticConstructor = "of")
public class SupportedProject implements Named, ProjectAware {

	@With Project project;
	SupportStatus status;

	public String getFolderName() {
		return status.name().toLowerCase() + "/" + project.getFolderName();
	}

	public String getName() {
		return project.getName();
	}

	public GitHubNamingStrategy getNamingStrategy() {
		return project.getNamingStrategy();
	}

	@Override
	public String toString() {
		return project.getFullName() + " (" + status.name() + ")";
	}

	@Override
	public SupportedProject getSupportedProject() {
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.model.SupportStatusAware#getSupportStatus()
	 */
	@Override
	public SupportStatus getSupportStatus() {
		return status;
	}

}
