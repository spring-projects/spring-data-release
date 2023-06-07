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
package org.springframework.data.release.build;

import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.release.model.ArtifactVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlbeam.annotation.XBDelete;
import org.xmlbeam.annotation.XBRead;
import org.xmlbeam.annotation.XBValue;
import org.xmlbeam.annotation.XBWrite;

/**
 * @author Oliver Gierke
 */
public interface Pom {

	@XBRead("/project")
	Artifact getArtifact();

	@XBRead("/project/version")
	String getRawVersion();

	@XBRead("/project/version")
	ArtifactVersion getVersion();

	@XBWrite("/project/version")
	void setVersion(ArtifactVersion version);

	@XBWrite("/project/parent/version")
	void setParentVersion(ArtifactVersion version);

	@XBRead("/project/properties/{0}")
	String getProperty(String property);

	@XBWrite("/project/properties/{0}")
	void setProperty(String property, @XBValue ArtifactVersion value);

	@XBWrite("/project/properties/{0}")
	void setProperty(String property, @XBValue String value);

	@XBWrite("/project/repositories/repository[id=\"{0}\"]/id")
	void setRepositoryId(String oldId, @XBValue String newId);

	@XBWrite("/project/repositories/repository[id=\"{0}\"]/url")
	void setRepositoryUrl(String id, @XBValue String url);

	@XBDelete("/project/repositories/*")
	void deleteRepositories();

	@XBWrite("/project/repositories")
	void setRepositories(Element repositories);

	/**
	 * Sets the version of the dependency with the given artifact identifier to the given {@link ArtifactVersion}.
	 *
	 * @param artifactId
	 * @param version
	 */
	@XBWrite("/project/dependencies/dependency[artifactId=\"{0}\"]/version")
	Pom setDependencyVersion(String artifactId, @XBValue ArtifactVersion version);

	@XBRead("/project/dependencies/dependency[artifactId=\"{0}\"]/version")
	String getDependencyVersion(String artifactId);

	@XBWrite("/project/dependencyManagement/dependencies/dependency[artifactId=\"{0}\"]/version")
	Pom setDependencyManagementVersion(String artifactId, @XBValue ArtifactVersion version);

	@XBRead("/project/dependencyManagement/dependencies/dependency[artifactId=\"{0}\"]")
	Artifact getManagedDependency(String artifactId);

	@XBRead("//dependency[substring(version, string-length(version) - string-length('-SNAPSHOT') + 1) = '-SNAPSHOT']")
	List<Artifact> getSnapshotDependencies();

	class RepositoryElementFactory {

		public static Element of(Repository... repositories) {
			return of(Arrays.asList(repositories));
		}

		public static Element of(List<Repository> repositories) {

			Document doc = createDocument();
			Element repos = doc.createElement("repositories");
			repos.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));
			repos.appendChild(doc.createTextNode("\t\t"));

			for (int i = 0; i < repositories.size(); i++) {

				Repository repo = repositories.get(i);

				repos.appendChild(toElement(doc, repo));
				repos.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));

				if (i + 1 == repositories.size()) {
					repos.appendChild(indent(doc, 1));
				} else {
					repos.appendChild(indent(doc, 2));
				}
			}

			return repos;
		}

		private static Element toElement(Document doc, Repository repo) {

			Element repository = doc.createElement("repository");

			repository.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));

			repository.appendChild(indent(doc, 3));
			repository.appendChild(createElement(doc, "id", repo.getId()));
			repository.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));

			repository.appendChild(indent(doc, 3));
			repository.appendChild(createElement(doc, "url", repo.getUrl()));
			repository.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));

			if (repo.getSnapshots() != null) {
				appendEnabledConfig(doc, "snapshots", repo.getSnapshots(), repository);
			}

			if (repo.getReleases() != null) {
				appendEnabledConfig(doc, "releases", repo.getReleases(), repository);
			}

			repository.appendChild(indent(doc, 2));

			return repository;
		}

		private static void appendEnabledConfig(Document doc, String tagName, boolean value, Element repository) {

			Element snapshots = doc.createElement(tagName);
			snapshots.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));

			snapshots.appendChild(indent(doc, 4));
			snapshots.appendChild(createElement(doc, "enabled", Boolean.toString(value)));
			snapshots.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));
			snapshots.appendChild(indent(doc, 3));

			repository.appendChild(indent(doc, 3));
			repository.appendChild(snapshots);
			repository.appendChild(doc.createTextNode(IOUtils.LINE_SEPARATOR));
		}

		private static Element createElement(Document doc, String name, String content) {
			Element url = doc.createElement(name);
			url.setTextContent(content);
			return url;
		}

		private static Node indent(Document doc, int indentSize) {
			return doc.createTextNode(StringUtils.repeat("\t", indentSize));
		}

		@SneakyThrows
		static Document createDocument() {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		}

	}

	public interface Artifact {

		@XBRead("child::groupId")
		GroupId getGroupId();

		@XBRead("child::artifactId")
		String getArtifactId();

		@XBRead("child::version")
		String getVersion();

		default String getArtifactPath() {
			return "/".concat(getGroupId().asPath()).concat("/").concat(getArtifactId());
		}

		default String getPath() {
			return getArtifactPath().concat(getVersion());
		}
	}
}
