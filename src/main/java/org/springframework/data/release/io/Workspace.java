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
package org.springframework.data.release.io;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;

import org.springframework.data.release.model.Project;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.SupportedProject;
import org.springframework.data.release.utils.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Abstraction of the workspace that is used to work with the {@link Project}'s repositories, execute builds, etc.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Workspace {

	private static final Charset UTF_8 = StandardCharsets.UTF_8;

	@NonNull IoProperties ioProperties;
	@NonNull Logger logger;

	/**
	 * Returns the current working directory.
	 *
	 * @return
	 */
	public File getWorkingDirectory() {
		return ioProperties.getWorkDir();
	}

	/**
	 * Returns the current staging directory.
	 *
	 * @return
	 */
	public File getStagingDirectory() {
		return ioProperties.getStagingDir();
	}

	/**
	 * Returns the current logs directory.
	 *
	 * @return
	 */
	public File getLogsDirectory() {
		return ioProperties.getLogs();
	}

	/**
	 * Cleans up the working directory by removing all files and folders in it.
	 *
	 * @throws IOException
	 */
	public void cleanup() throws IOException {

		delete(getWorkingDirectory().toPath(), "workspace");
		delete(getStagingDirectory().toPath(), "staging");
		delete(getLogsDirectory().toPath(), "logs");
	}

	public void delete(Path path, String type) throws IOException {

		logger.log("Workspace", "Cleaning up %s directory at %s.", type, path.toAbsolutePath());

		purge(path, it -> !path.equals(it));
	}

	public void purge(Path path, Predicate<Path> filter) throws IOException {

		if (!path.toFile().exists()) {
			return;
		}

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

				if (filter.test(file)) {
					Files.delete(file);
				}

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {

				if (filter.test(dir)) {
					Files.delete(dir);
				}

				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Returns the directory for the given {@link Project}.
	 *
	 * @param project must not be {@literal null}.
	 * @return
	 */
	public File getProjectDirectory(SupportedProject project) {

		Assert.notNull(project, "Project must not be null!");

		if (project.getProject() == Projects.SMOKE_TESTS) {
			return new File("smoke-tests");
		}

		return new File(getWorkingDirectory(), project.getFolderName());
	}

	/**
	 * Returns whether the project directory for the given project already exists.
	 *
	 * @param project must not be {@literal null}.
	 * @return
	 */
	public boolean hasProjectDirectory(SupportedProject project) {

		Assert.notNull(project, "Project must not be null!");
		return getProjectDirectory(project).exists();
	}

	/**
	 * Returns a file with the given name relative to the working directory for the given {@link Project}.
	 *
	 * @param name must not be {@literal null} or empty.
	 * @param project must not be {@literal null}.
	 * @return
	 */
	public File getFile(String name, SupportedProject project) {

		Assert.hasText(name, "Filename must not be null or empty!");
		Assert.notNull(project, "Project must not be null!");

		return new File(getProjectDirectory(project), name);
	}

	public boolean processFile(String filename, SupportedProject project, LineCallback callback) {

		File file = getFile(filename, project);

		if (!file.exists()) {
			return false;
		}

		StringBuilder builder = new StringBuilder();

		try (Scanner scanner = new Scanner(file)) {

			long number = 0;

			while (scanner.hasNextLine()) {
				callback.doWith(scanner.nextLine(), number++).ifPresent(it -> builder.append(it).append("\n"));
			}

			writeContentToFile(filename, project, builder.toString());

		} catch (Exception o_O) {
			throw new RuntimeException(o_O);
		}

		return true;
	}

	private void writeContentToFile(String name, SupportedProject project, String content) throws IOException {

		File file = getFile(name, project);
		Files.write(file.toPath(), Collections.singleton(content), UTF_8);
	}

	/**
	 * Initializes the working directory and creates the folders if necessary.
	 *
	 * @throws IOException
	 */
	@PostConstruct
	public void setUp() throws IOException {

		Path path = getWorkingDirectory().toPath();

		if (!java.nio.file.Files.exists(path)) {
			java.nio.file.Files.createDirectories(path);
		}
	}

	public interface LineCallback {
		Optional<String> doWith(String line, long number);
	}
}
