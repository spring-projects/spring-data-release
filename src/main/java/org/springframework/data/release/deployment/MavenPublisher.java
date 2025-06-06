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

import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.TrainIteration;
import org.springframework.data.release.utils.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Component to publish artifacts to Maven Central using the Maven Publisher API.
 *
 * @author Mark Paluch
 */
@Component
public class MavenPublisher {

	private static final String UPLOAD_URI = "/api/v1/publisher/upload?name={deploymentName}&publishingType=USER_MANAGED";
	private static final String DEPLOYMENT_STATUS = "/api/v1/publisher/status?id={deploymentId}";
	private static final String PUBLISH_DEPLOYMENT = "/api/v1/publisher/deployment/{deploymentId}";

	private final Logger logger;
	private final Workspace workspace;
	private final DeploymentProperties properties;
	private final RestOperations restTemplate;

	public MavenPublisher(Logger logger, Workspace workspace, DeploymentProperties properties,
			RestTemplateBuilder builder) {

		this.logger = logger;
		this.workspace = workspace;
		this.properties = properties;
		this.restTemplate = createOperations(
				builder.additionalMessageConverters(new FormHttpMessageConverter(), new StringHttpMessageConverter()),
				properties.getMavenCentral());
	}

	private static RestOperations createOperations(RestTemplateBuilder templateBuilder,
			DeploymentProperties.MavenCentral properties) {

		String authentication = "Bearer " + properties.getBearer().toString();
		return templateBuilder.uriTemplateHandler(new DefaultUriBuilderFactory(properties.getCentralApiBaseUrl()))
				.defaultHeader(HttpHeaders.AUTHORIZATION, authentication).build();
	}

	/**
	 * Initialize and clear the staging repository.
	 *
	 * @return
	 */
	@SneakyThrows
	public StagingRepository initializeStagingRepository() {

		File stagingDirectory = getStagingDirectory();
		File zip = getStagingFile();
		workspace.delete(stagingDirectory.toPath(), "central-staging");

		if (zip.exists()) {
			if (!zip.delete()) {
				throw new IllegalStateException(String.format("Unable to delete '%s'", zip));
			}
		}

		if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
			throw new IllegalStateException(String.format("Unable to create '%s'", stagingDirectory));
		}

		String[] list = stagingDirectory.list();

		if (list == null) {
			throw new IllegalStateException(String.format("Staging directory cannot be listed '%s'", stagingDirectory));
		}

		if (list.length != 0) {
			throw new IllegalStateException(String.format("Staging directory is not empty: %s", Arrays.asList(list)));
		}

		return getStagingRepository(stagingDirectory);
	}

	/**
	 * Return the staging repository.
	 *
	 * @return
	 */
	public StagingRepository getStagingRepository() {
		return getStagingRepository(getStagingDirectory());
	}

	private StagingRepository getStagingRepository(File stagingDirectory) {
		return LocalStagingRepository.of(stagingDirectory);
	}

	/**
	 * Upload a compressed version of the staged artifacts to Maven Publisher.
	 *
	 * @param iteration
	 * @param deploymentName
	 * @param localStaging
	 * @return
	 * @throws IOException
	 */
	public StagingRepository upload(ModuleIteration iteration, String deploymentName, StagingRepository localStaging)
			throws IOException {

		Assert.notNull(localStaging, "Local StagingRepository must not be null");
		Assert.isTrue(localStaging.isPresent(), "Local StagingRepository must be present");
		Assert.isInstanceOf(LocalStagingRepository.class, localStaging);

		File zipFile = compressStagedArtifacts(iteration.getTrainIteration(), (LocalStagingRepository) localStaging);

		return uploadStagingFile(iteration, deploymentName, zipFile);
	}

	private StagingRepository uploadStagingFile(ModuleIteration iteration, String deploymentName, File zipFile) {

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.setAccept(Collections.singletonList(MediaType.TEXT_PLAIN));

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("bundle", new FileSystemResource(zipFile));

		logger.log(iteration, "🚛 Uploading staging file …");
		ResponseEntity<String> upload = restTemplate.postForEntity(UPLOAD_URI, new HttpEntity<>(body, headers),
				String.class, Collections.singletonMap("deploymentName", deploymentName));

		if (upload.getStatusCode().is2xxSuccessful()) {

			String deploymentId = upload.getBody();
			logger.log(iteration, "📦 Staging file uploaded successfully. Created deploymentId '%s'", deploymentId);
			return StagingRepository.of(deploymentId);
		}

		throw new IllegalStateException(
				String.format("😵‍💫 Staging upload of '%s' failed: %s %s", zipFile, upload.getStatusCode(), upload.getBody()));
	}

	/**
	 * Retrieve the deployment status.
	 *
	 * @param stagingRepository
	 * @return
	 */
	public DeploymentStatus getStatus(StagingRepository stagingRepository) {

		Assert.notNull(stagingRepository, "StagingRepository must not be null");
		Assert.isTrue(stagingRepository.isPresent(), "StagingRepository must be present");

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		ResponseEntity<DeploymentStatus> status = restTemplate.exchange(DEPLOYMENT_STATUS, HttpMethod.POST,
				new HttpEntity<>(headers), DeploymentStatus.class,
				Collections.singletonMap("deploymentId", stagingRepository.getId()));

		if (status.getStatusCode().is2xxSuccessful()) {
			return status.getBody();
		}

		throw new IllegalStateException(
				String.format("😵‍💫 Obtaining deployment status for deploymentId '%s' failed: %s %s", stagingRepository,
						status.getStatusCode(), status.getBody()));
	}

	/**
	 * Publish the deployment to Maven Central.
	 *
	 * @param iteration
	 * @param stagingRepository
	 */
	public void publish(TrainIteration iteration, StagingRepository stagingRepository) {

		Assert.notNull(stagingRepository, "StagingRepository must not be null");
		Assert.isTrue(stagingRepository.isPresent(), "StagingRepository must be present");

		logger.log(iteration, "🛳️ Publishing deployment '%s'…", stagingRepository.getId());

		ResponseEntity<String> publish = restTemplate.postForEntity(PUBLISH_DEPLOYMENT, null, String.class,
				Collections.singletonMap("deploymentId", stagingRepository.getId()));

		if (publish.getStatusCodeValue() == HttpStatus.NO_CONTENT.value()) {
			logger.log(iteration, "🚀 Au revoir. Bye bye. See you later at Maven Central.");
			return;
		}

		throw new IllegalStateException(String.format("😵‍💫 Publishing deployment for deploymentId '%s' failed: %s %s",
				stagingRepository, publish.getStatusCode(), publish.getBody()));
	}

	File compressStagedArtifacts(TrainIteration iteration, LocalStagingRepository stagingDirectory) throws IOException {

		File zipFile = getStagingFile();

		logger.log(iteration, "🗜️ Creating staging file '%s'…", zipFile);
		FileOutputStream zip = new FileOutputStream(zipFile);
		ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(zip));

		AtomicLong counter = new AtomicLong();
		Path root = stagingDirectory.getFile().toPath();
		Files.walkFileTree(root, new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

				if (file.getFileName().toString().contains("maven-metadata.")
						|| file.getFileName().toString().startsWith(".")) {
					return FileVisitResult.CONTINUE;
				}

				String entry = root.relativize(file).toString();
				zos.putNextEntry(new ZipEntry(entry));
				Files.copy(file, zos);
				zos.closeEntry();

				counter.incrementAndGet();

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				throw exc;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {

				if (exc != null) {
					throw exc;
				}
				return FileVisitResult.CONTINUE;
			}
		});

		zos.close();

		if (counter.get() == 0) {
			throw new IllegalStateException(String.format("Staging directory '%s' empty", stagingDirectory));
		}

		logger.log(iteration, "🗜️ Created staging file '%s' with %d files", zipFile, counter.get());

		return zipFile;
	}

	/**
	 * Validate the deployment.
	 *
	 * @param iteration
	 * @param deploymentId
	 * @return
	 * @throws TimeoutException
	 */
	public DeploymentStatus validate(TrainIteration iteration, StagingRepository deploymentId)
			throws TimeoutException, InterruptedException {

		long maxWaitSeconds = TimeUnit.MILLISECONDS
				.toSeconds(properties.getMavenCentral().getValidationTimeout().toMillis());

		Instant start = Instant.now();
		boolean validating = false;
		while (Duration.between(start, Instant.now()).get(ChronoUnit.SECONDS) < maxWaitSeconds) {

			DeploymentStatus status = getStatus(deploymentId);

			switch (status.getDeploymentState()) {

				case PENDING:
				case VALIDATING:
					if (!validating) {
						logger.log(iteration, "⏳ Validation. Waiting for completion…");
						validating = true;
					}
					break;

				case VALIDATED:
				case PUBLISHED:
					logger.log(iteration, "✅ Validation successful.");
					return status;

				case FAILED:
					logger.log(iteration, "⚠️ Validation failed: %s", status.getErrorDetail());
					throw new IllegalStateException("Deployment Validation failed");
			}

			TimeUnit.SECONDS.sleep(5);
		}

		throw new TimeoutException(String.format("Validation timeout '%d seconds' exceeded: %d seconds", maxWaitSeconds,
				Duration.between(start, Instant.now()).get(ChronoUnit.SECONDS)));
	}

	File getStagingDirectory() {
		return new File(workspace.getStagingDirectory(), "central-staging");
	}

	File getStagingFile() {
		return new File(workspace.getStagingDirectory(), "central-staging.zip");
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@Data
	public static class DeploymentStatus {

		private String deploymentId;
		private DeploymentState deploymentState;
		private Map<String, List<String>> errors = new LinkedHashMap<>();
		private List<String> purls = new ArrayList<>();

		public String getErrorDetail() {

			StringBuilder errorMessage = new StringBuilder();

			errorMessage.append("\n\n").append("Deployment ").append(getDeploymentId()).append(" failed\n");

			for (Map.Entry<String, List<String>> errorCategory : getErrors().entrySet()) {

				errorMessage.append(errorCategory.getKey()).append(":\n");

				for (String error : errorCategory.getValue()) {
					errorMessage.append(" - ").append(error).append("\n");
				}
				errorMessage.append("\n");
			}

			return errorMessage.toString();
		}

		enum DeploymentState {
			PENDING, VALIDATING, VALIDATED, PUBLISHING, PUBLISHED, FAILED
		}

	}

	@Getter
	static class LocalStagingRepository extends StagingRepository {

		private final File file;

		LocalStagingRepository(File file) {
			super(String.format("central-staging::default::file://%s", file.getAbsolutePath()));
			this.file = file;
		}

		public static StagingRepository of(File file) {

			Assert.notNull(file, "File must not be null!");
			Assert.isTrue(file.exists(), () -> String.format("File '%s' must exist!", file));

			return new LocalStagingRepository(file);
		}

	}
}
