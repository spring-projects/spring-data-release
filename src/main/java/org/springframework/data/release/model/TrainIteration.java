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

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.Iterator;
import java.util.List;

import org.springframework.data.util.Streamable;

/**
 * @author Oliver Gierke
 */
@Value
@RequiredArgsConstructor
public class TrainIteration implements Streamable<ModuleIteration>, Lifecycle {

	private final Train train;
	private final Iteration iteration;

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ModuleIteration> iterator() {
		return train.getModuleIterations(iteration).iterator();
	}

	public ArtifactVersion getModuleVersion(Project project) {
		return train.getModuleVersion(project, iteration);
	}

	public ModuleIteration getModule(Project project) {
		return train.getModuleIteration(project, iteration);
	}

	public List<ModuleIteration> getModulesExcept(Project... exclusions) {
		return train.getModuleIterations(iteration, exclusions);
	}

	public boolean contains(Project project) {
		return train.contains(project);
	}

	public ModuleIteration getPreviousIteration(ModuleIteration module) {

		Iteration previousIteration = train.getIterations().getPreviousIteration(iteration);
		return train.getModuleIteration(module.getProject(), previousIteration);
	}

	public String getName() {

		if (getTrain().usesCalver()) {
			return getCalver().toMajorMinorBugfix();
		}

		return getTrain().getName();
	}

	public String getReleaseTrainNameAndVersion() {

		if (getTrain().usesCalver()) {

			if (getIteration().isMilestone() || getIteration().isReleaseCandidate()) {
				return "%s-%s".formatted(getCalver().toMajorMinorBugfix(), iteration);
			}

			return getCalver().toMajorMinorBugfix();
		}

		String trainName = getTrain().getName();

		return iteration.isGAIteration()
				? "%s-RELEASE".formatted(trainName)
				: "%s-%s".formatted(trainName, iteration);
	}

	public SupportedProject getSupportedProject(Project project) {
		return train.getSupportedProject(project);
	}

	public SupportedProject getSupportedProject(Module module) {
		return train.getSupportedProject(module);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		if (getTrain().usesCalver()) {
			return getCalver().toMajorMinorBugfix();
		}

		return String.format("%s %s", getTrain().getName(), iteration.getName());
	}

	public Version getCalver() {

		Version version = getTrain().getCalver();

		return version.withBugfix(getIteration().getBugfixValue());
	}

	public String getNextBugfixName() {

		Version version = getTrain().getCalver();

		if (getIteration().isGAIteration() || getIteration().isServiceIteration()) {
			return version.withBugfix(getIteration().getBugfixValue() + 1).toMajorMinorBugfix();
		}

		return version.toMajorMinorBugfix();
	}

	public String getNextIterationName() {

		Version version = getTrain().getCalver().nextMinor();

		return version.toMajorMinorBugfix();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.model.SupportStatusAware#getSupportStatus()
	 */
	@Override
	public SupportStatus getSupportStatus() {
		return train.getSupportStatus();
	}
}
