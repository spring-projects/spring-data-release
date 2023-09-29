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
package org.springframework.data.release.model;

import lombok.Value;

import java.util.Locale;

import org.springframework.data.release.model.Train.DocumentationFormat;

/**
 * Value object providing documentation links.
 *
 * @author Mark Paluch
 */
@Value(staticConstructor = "of")
public class DocumentationMetadata {

	private static String DOCS_BASE = "https://docs.spring.io/spring-data/%s/docs/%s";

	private static String ANTORA_BASE = "https://docs.spring.io/spring-data/%s/reference/";
	private static String DOCS = DOCS_BASE.concat("/reference/html/");
	private static String JAVADOC = DOCS_BASE.concat("/api/");

	DocumentationFormat documentationFormat;
	Project project;
	ArtifactVersion version;
	boolean isCurrent;

	public static DocumentationMetadata of(ModuleIteration module, ArtifactVersion version, boolean isCurrent) {
		return of(module.getTrain().getDocumentationFormat(), module.getProject(), version, isCurrent);
	}

	/**
	 * Returns the JavaDoc URL for non-snapshot versions and not the build project.
	 *
	 * @return
	 */
	public String getApiDocUrl() {

		if (version.isSnapshotVersion()) {
			return "";
		}

		if (Projects.BUILD.equals(project)) { // Report Commons Docs for Spring Data Build
			return String.format(JAVADOC, getProjectName(Projects.COMMONS), getDocumentationVersion());
		}

		return String.format(JAVADOC, project == Projects.R2DBC ? "r2dbc" : getProjectName(project),
				getDocumentationVersion());
	}

	private String getProjectName(Project project) {

		// With Asciidoctor, JDBC had its own docs path
		if (documentationFormat == DocumentationFormat.ASCIIDOC) {
			if (project == Projects.RELATIONAL) {
				return "jdbc";
			}
		}

		// With Antora, JDBC and R2DBC use a shared site
		if (documentationFormat == DocumentationFormat.ANTORA) {
			if (project == Projects.R2DBC || project == Projects.JDBC) {
				return "relational";
			}
		}

		return project.getName().toLowerCase(Locale.US);
	}

	/**
	 * Returns the reference documentation URL for non-snapshot versions and not the build project.
	 *
	 * @return
	 */
	public String getReferenceDocUrl() {

		if (documentationFormat == DocumentationFormat.ASCIIDOC) {

			if (version.isSnapshotVersion()) {
				return "";
			}

			Project project = this.project;

			if (Projects.BUILD.equals(project)) { // Report Commons Docs for Spring Data Build
				project = Projects.COMMONS;
			}

			return String.format(DOCS, getProjectName(project), getDocumentationVersion());
		}

		return String.format(ANTORA_BASE, getProjectName(project));
	}

	public String getVersionOrTrainName(Train train) {

		if (Projects.BUILD.equals(project)) {

			if (train.usesCalver()) {

				if (version.isBugFixVersion() || version.isReleaseVersion()) {
					return train.getCalver().withBugfix(version.getVersion().getBugfix()).toMajorMinorBugfix();
				}

				return String.format("%s-%s",
						train.getCalver().withBugfix(version.getVersion().getBugfix()).toMajorMinorBugfix(),
						version.getReleaseTrainSuffix());
			}

			return String.format("%s-%s", train.getName(),
					version.isReleaseVersion() && !version.isBugFixVersion() ? "RELEASE" : version.getReleaseTrainSuffix());
		}

		return version.toString();
	}

	public String getDocumentationVersion() {
		return isCurrent ? "current" : version.toString();
	}

}
