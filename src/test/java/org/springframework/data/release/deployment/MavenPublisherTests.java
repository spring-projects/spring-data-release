/*
 * Copyright 2025 the original author or authors.
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
package org.springframework.data.release.deployment;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.release.io.IoProperties;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.Password;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.utils.Logger;

/**
 * Tests for {@link MavenPublisher}.
 *
 * @author Mark Paluch
 */
class MavenPublisherTests {

	@TempDir File stagingDir;
	private MavenPublisher publisher;

	@BeforeEach
	void setUp() {

		IoProperties io = new IoProperties();
		io.setStagingDir(stagingDir.getAbsolutePath());

		Workspace workspace = new Workspace(io, new Logger());
		DeploymentProperties deploymentProperties = new DeploymentProperties();

		DeploymentProperties.MavenCentral central = new DeploymentProperties.MavenCentral();
		central.setUsername("foo");
		central.setPassword(Password.of("bar"));
		deploymentProperties.setMavenCentral(central);

		publisher = new MavenPublisher(new Logger(), workspace, deploymentProperties, new RestTemplateBuilder());
	}

	@Test
	void shouldInitializeEmptyStagingRepository() {

		MavenPublisher.LocalStagingRepository stagingRepository = (MavenPublisher.LocalStagingRepository) publisher
				.initializeStagingRepository();

		assertThat(stagingRepository.getFile()).isEqualTo(new File(stagingDir, "central-staging"));
		assertThat(stagingRepository.getFile()).exists();
	}

	@Test
	void shouldPurgeStagingRepository() throws IOException {

		File inner = new File(stagingDir, "central-staging/foo");
		inner.mkdirs();

		Files.write(new File(stagingDir, "central-staging.zip").toPath(), "foo".getBytes());

		publisher.initializeStagingRepository();

		assertThat(stagingDir.list()).hasSize(1).contains("central-staging");
		assertThat(new File(stagingDir, "central-staging")).isEmptyDirectory();
	}

	@Test
	void shouldCompressStagingContent() throws IOException {

		MavenPublisher.LocalStagingRepository stagingRepository = (MavenPublisher.LocalStagingRepository) publisher
				.initializeStagingRepository();

		File directory = new File(publisher.getStagingDirectory(), "foo/bar");
		directory.mkdirs();

		Files.write(new File(directory, "baz.txt").toPath(), "foo".getBytes());
		Files.write(new File(directory, ".DS_Store").toPath(), "foo".getBytes());
		Files.write(new File(directory, "maven-metadata.xml").toPath(), "foo".getBytes());
		Files.write(new File(directory, "maven-metadata.xml.md5").toPath(), "foo".getBytes());

		publisher.compressStagedArtifacts(ReleaseTrains.Z.getIteration(Iteration.M1), stagingRepository);

		ZipFile zf = new ZipFile(publisher.getStagingFile());
		List<String> entries = Collections.list(zf.entries()).stream().map(ZipEntry::getName).collect(Collectors.toList());
		zf.close();

		assertThat(entries).hasSize(1).contains("foo/bar/baz.txt");
	}
}
