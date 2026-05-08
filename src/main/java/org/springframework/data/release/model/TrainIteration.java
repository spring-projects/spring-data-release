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
	private final Hotfix hotfix;

	/*
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ModuleIteration> iterator() {
		return train.getModuleIterations(iteration, hotfix).iterator();
	}

	public ArtifactVersion getModuleVersion(Project project) {
		return train.getModuleVersion(project, iteration, hotfix);
	}

	public ModuleIteration getModule(Project project) {
		return train.getModuleIteration(project, iteration, hotfix);
	}

	public List<ModuleIteration> getModulesExcept(Project... exclusions) {
		return train.getModuleIterations(iteration, hotfix, exclusions);
	}

	public boolean contains(Project project) {
		return train.contains(project);
	}

	public TrainIteration nextIteration() {
		return train.getIteration(iteration.getNext());
	}

	public String getName() {

		if (getTrain().usesCalver()) {
			return getCalver().toFullVersion();
		}

		return getTrain().getName();
	}

	public String getReleaseTrainNameAndVersion() {

		if (getTrain().usesCalver()) {

			if (getIteration().isMilestone() || getIteration().isReleaseCandidate()) {
				return String.format("%s-%s", getCalver().toFullVersion(), iteration);
			}

			return getCalver().toFullVersion();
		}

		String trainName = getTrain().getName();

		return iteration.isGAIteration() ? String.format("%s-RELEASE", trainName)
				: String.format("%s-%s", trainName, iteration);
	}

	public SupportedProject getSupportedProject(Project project) {
		return train.getSupportedProject(project);
	}

	public SupportedProject getSupportedProject(Module module) {
		return train.getSupportedProject(module);
	}

	public Version getCalver() {

		Version version = getTrain().getCalver().withBugfix(getIteration().getBugfixValue());
		if (hotfix.isHotfix()) {
			version = version.withBuild(hotfix.getValue());
		}

		return version;
	}

	public String getNextBugfixName() {

		Version version = getTrain().getCalver();

		if (getIteration().isGAIteration() || getIteration().isServiceIteration()) {
			return version.withBugfix(getIteration().getBugfixValue() + 1).toMajorMinorBugfix();
		}

		return version.toMajorMinorBugfix();
	}

	public String getNextHotfixName() {

		Version version = getTrain().getCalver();

		if (getIteration().isServiceIteration()) {
			return version.withBuild(hotfix.increment().getValue()).toFullVersion();
		}

		return version.toMajorMinorBugfix();
	}

	public String getNextIterationName() {

		Version version = getTrain().getCalver().nextMinor();
		return version.toFullVersion();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.model.SupportStatusAware#getSupportStatus()
	 */
	@Override
	public SupportStatus getSupportStatus() {
		return train.getSupportStatus();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		if (getTrain().usesCalver()) {
			if (iteration.isPreview()) {
				return String.format("%s-%s", getCalver().toFullVersion(), iteration.getName());
			}
			return getCalver().toFullVersion();
		}

		return String.format("%s %s", getTrain().getName(), iteration.getName());
	}

}
