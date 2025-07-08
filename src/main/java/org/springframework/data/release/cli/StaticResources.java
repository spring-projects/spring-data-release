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
package org.springframework.data.release.cli;

import lombok.RequiredArgsConstructor;

import org.springframework.data.release.git.GitProject;
import org.springframework.data.release.git.Tag;
import org.springframework.data.release.git.VersionTags;
import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.data.release.model.DocumentationMetadata;
import org.springframework.data.release.model.ModuleIteration;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@RequiredArgsConstructor
public class StaticResources {

	private final DocumentationMetadata metadata;

	private final String releaseUrl;

	public StaticResources(ModuleIteration module) {

		this.metadata = DocumentationMetadata.of(module, ArtifactVersion.of(module), false);

		GitProject gitProject = GitProject.of(module);
		Tag tag = VersionTags.empty(module.getProject()).createTag(module);

		this.releaseUrl = "%s/releases/tag/%s".formatted(gitProject.getProjectUri(), tag.getName());
	}

	public String getDocumentationUrl() {
		return metadata.getReferenceDocUrl();
	}

	public String getJavaDocUrl() {
		return metadata.getApiDocUrl();
	}

	public String getChangelogUrl() {
		return releaseUrl;
	}
}
