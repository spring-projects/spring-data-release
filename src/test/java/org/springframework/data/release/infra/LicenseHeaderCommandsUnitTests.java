/*
 * Copyright 2014-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.release.infra.LicenseHeaderCommands.FileType;
import org.springframework.data.release.model.SupportStatus;

/**
 * Unit tests for {@link LicenseHeaderCommands}.
 *
 * @author Chris Bono
 */
class LicenseHeaderCommandsUnitTests {

	private LicenseHeaderCommands commands;

	@BeforeEach
	void prepareForTest() {
		commands = new LicenseHeaderCommands(mock(), mock(), mock(), mock(), mock());
	}

	@Nested
	class WithOpenSourceFilesTests {

		@Test
		void javaHeaderWithStartDateOnlyUpdatedToPresent() {
			var originalContent = openSourceJavaFile("2022");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, originalContent, "present",
					SupportStatus.OSS);
			var expectedContent = openSourceJavaFile("2022-present");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void javaHeaderWithStartAndEndDateUpdatedToPresent() {
			var originalContent = openSourceJavaFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, originalContent, "present",
					SupportStatus.OSS);
			var expectedContent = openSourceJavaFile("2022-present");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void javaHeaaderUpdateIsIdempotent() {
			var originalContent = openSourceJavaFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, originalContent, "present",
					SupportStatus.OSS);
			updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, updatedContent, "present", SupportStatus.OSS);
			var expectedContent = openSourceJavaFile("2022-present");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void adocWithStartAndEndDateUpdatedToPresent() {
			var originalContent = adocFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.ADOC, originalContent, "present",
					SupportStatus.OSS);
			var expectedContent = adocFile("2022-present");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void pomXmlWithStartAndEndDateUpdatedToPresent() {
			var originalContent = pomFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.POM_XML, originalContent, "present",
					SupportStatus.OSS);
			var expectedContent = pomFile("2022-present");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}
	}

	@Nested
	class WithCommercialJavaFilesTests {

		@Test
		void javaHeaderWithStartDateOnlyUpdatedToPresent() {
			var originalContent = openSourceJavaFile("2022");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, originalContent, "present",
					SupportStatus.COMMERCIAL);
			var expectedContent = commercialJavaFile("2022");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void javaHeaderWithStartAndEndDateUpdatedToPresent() {
			var originalContent = openSourceJavaFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, originalContent, "present",
					SupportStatus.COMMERCIAL);
			var expectedContent = commercialJavaFile("2022");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void javaHeaderUpdateIsIdempotent() {
			var originalContent = openSourceJavaFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, originalContent, "present",
					SupportStatus.COMMERCIAL);
			updatedContent = commands.updateLicenseHeaderInFile(FileType.JAVA, updatedContent, "present",
					SupportStatus.COMMERCIAL);
			var expectedContent = commercialJavaFile("2022");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void adocWithStartAndEndDateUpdatedToPresent() {
			var originalContent = adocFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.ADOC, originalContent, "present",
					SupportStatus.COMMERCIAL);
			var expectedContent = adocFile("2022-present");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

		@Test
		void pomXmlWithStartAndEndDateUpdatedToPresent() {
			var originalContent = pomFile("2022-2024");
			var updatedContent = commands.updateLicenseHeaderInFile(FileType.POM_XML, originalContent, "present",
					SupportStatus.COMMERCIAL);
			var expectedContent = pomFile("2022-present");
			assertThat(updatedContent).isEqualTo(expectedContent);
		}

	}

	private String openSourceJavaFile(String dateRange) {
		return """
				/*
				 * Copyright %s the original author or authors.
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
				package some.package;
				
				import some.otherpackage.Foo;
				
				/**
				 * This is some class comment.
				 */
				public class SomeClass {
				}""".formatted(dateRange);
	}

	private String commercialJavaFile(String startDate) {
		return """
				/*
				 * Copyright %s Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
				 * Copyright %s-present the original author or authors.
				 *
				 *
				 *
				 *
				 *
				 *
				 *
				 *
				 *
				 *
				 *
				 */
				package some.package;
				
				import some.otherpackage.Foo;
				
				/**
				 * This is some class comment.
				 */
				public class SomeClass {
				}""".formatted(startDate, startDate);
	}

	private String adocFile(String dateRange) {
		return """
Some adoc content.

(C) 2008-{copyright-year} VMware, Inc.

Copies of this document may be made for your own use and for distribution to others
				""";
	}

	private String pomFile(String dateRange) {
		return """
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <name>Spring Data Release Train - BOM Infrastructure</name>
    <licenses>
        <license>
            <name>Apache License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0</url>
            <comments>
                Copyright %s the original author or authors.

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                https://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
                implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            </comments>
        </license>
    </licenses>
    <profiles>
        <profile>
            <id>some-profile</id>
        </profile>
    </profiles>
</project>
		""".formatted(dateRange);
	}

}
