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
package org.springframework.data.release.infra;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;

import org.springframework.data.release.build.MavenArtifact;
import org.springframework.data.release.git.Branch;
import org.springframework.data.release.git.GitProject;
import org.springframework.data.release.model.DocumentationMetadata;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.utils.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Processes README files with support for asciidoc-style include directives and variable substitution.
 *
 * @author Mark Paluch
 */
@Component
class ReadmeProcessor {

	private static final Pattern INCLUDE = Pattern.compile("^include::([^\\[]+)\\[(.*?)]\\s*$");

	private final Logger logger;

	private final RestTemplate restTemplate = new RestTemplate();

	private final Map<URI, String> includesCache = new ConcurrentHashMap<>();

	ReadmeProcessor(Logger logger) {
		this.logger = logger;
	}

	public String preprocess(File source, ModuleIteration module) throws IOException {

		logger.log(module, "Rendering " + source.getName() + "…");

		MavenArtifact mavenArtifact = new MavenArtifact(module);
		DocumentationMetadata documentation = DocumentationMetadata.of(module, mavenArtifact.getVersion(), true);
		Branch branch = Branch.from(module);

		Map<String, String> variables = new HashMap<>();

		variables.put("documentation.reference", documentation.getReferenceDocUrl());
		GitProject gitProject = GitProject.of(module);
		variables.put("project.github.url", gitProject.getProjectUri());
		variables.put("project.full-name", module.getProject().getFullName());
		variables.put("project.artifactId", mavenArtifact.getArtifactId());
		variables.put("${version}", "${version}");
		variables.put("branch", branch.toString());

		return preprocess(module, source, variables);
	}

	public String preprocess(ModuleIteration module, File source, Map<String, String> variables) throws IOException {

		StringBuffer buffer = new StringBuffer();
		preprocess(module, source.toURI(), true, buffer);
		return StringSubstitutor.replace(buffer.toString(), variables);
	}

	private void preprocess(ModuleIteration module, URI source, boolean allowFileSource, Appendable out)
			throws IOException {

		try (BufferedReader reader = open(module, source, allowFileSource)) {
			String line;
			while ((line = reader.readLine()) != null) {

				Matcher m = INCLUDE.matcher(line);

				if (m.matches()) {
					String target = m.group(1).trim();
					URI resolved = resolve(source, target);
					preprocess(module, resolved, false, out);
				} else {
					out.append(line).append("\n");
				}
			}
		}
	}

	private BufferedReader open(ModuleIteration module, URI uri, boolean fileAllowed) throws IOException {

		if (uri.getScheme() == null || "file".equals(uri.getScheme())) {
			if (!fileAllowed) {
				throw new IllegalStateException("File access not allowed for URI: " + uri);
			}

			Path path = Paths.get(uri);
			return Files.newBufferedReader(path, StandardCharsets.UTF_8);
		}

		return new BufferedReader(new StringReader(includesCache.computeIfAbsent(uri, it -> {

			logger.log(module, "Fetching " + it + "…");
			return restTemplate.getForObject(it, String.class);

		})));
	}

	private static URI resolve(URI base, String target) {

		if (target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
			return URI.create(target);
		}

		// Relative include
		if (base.getScheme() == null || "file".equals(base.getScheme())) {
			Path baseDir = Paths.get(base).getParent();
			return baseDir.resolve(target).normalize().toUri();
		}

		return base.resolve(target);
	}

}
