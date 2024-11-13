/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.data.release.git;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.release.model.Project;

/**
 * Value object to capture source and target branches for a {@link Project}
 *
 * @author Mark Paluch
 */
public class BranchMapping {

	public static final BranchMapping NONE = new BranchMapping(Collections.emptyMap(), Collections.emptyMap());

	private final Map<Project, Branch> sourceBranches;

	private final Map<Project, Branch> targetBranches;

	public BranchMapping() {
		this(new HashMap<>(), new HashMap<>());
	}

	private BranchMapping(Map<Project, Branch> sourceBranches, Map<Project, Branch> targetBranches) {
		this.sourceBranches = sourceBranches;
		this.targetBranches = targetBranches;
	}

	public void add(Project project, Branch sourceBranch, Branch targetBranch) {
		sourceBranches.put(project, sourceBranch);
		targetBranches.put(project, targetBranch);
	}

	public Branch getSourceBranch(Project project) {
		return sourceBranches.get(project);
	}

	public Branch getTargetBranch(Project project) {
		return targetBranches.get(project);
	}

	public boolean hasBranches() {
		return !sourceBranches.isEmpty() && !targetBranches.isEmpty();
	}

}
