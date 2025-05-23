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

import lombok.Value;

/**
 * @author Oliver Gierke
 */

@Value
public class Repository {

	static Repository SNAPSHOT = new Repository("spring-snapshot", "https://repo.spring.io/snapshot", true, false);
	static Repository MILESTONE = new Repository("spring-milestone", "https://repo.spring.io/milestone", null, null);

	static Repository COMMERCIAL_SNAPSHOT = new Repository("spring-enterprise-snapshot",
			"https://usw1.packages.broadcom.com/artifactory/spring-enterprise-maven-dev-local", true, false);
	static Repository COMMERCIAL_RELEASE = new Repository("spring-enterprise-release",
			"https://usw1.packages.broadcom.com/artifactory/spring-enterprise-maven-prod-local", false, true);

	String id, url;
	Boolean snapshots;
	Boolean releases;
}
