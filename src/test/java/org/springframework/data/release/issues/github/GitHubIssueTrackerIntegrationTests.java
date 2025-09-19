/*
 * Copyright 2016-2022 the original author or authors.
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
package org.springframework.data.release.issues.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;

import java.util.Collection;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.release.AbstractIntegrationTests;
import org.springframework.data.release.WireMockExtension;
import org.springframework.data.release.issues.Ticket;
import org.springframework.data.release.issues.TicketReference;
import org.springframework.data.release.model.Iteration;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.data.release.model.Projects;
import org.springframework.data.release.model.ReleaseTrains;
import org.springframework.data.release.model.Train;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;

/**
 * Integration Tests for {@link GitHub} using a local {@link WireMockExtension} server.
 *
 * @author Mark Paluch
 */
@Disabled("Some strange test failures. Tests fail when running all, tests pass when running individually.")
class GitHubIssueTrackerIntegrationTests extends AbstractIntegrationTests {

	static final String ISSUES_URI = "/repos/spring-projects/spring-data-build/issues";
	static final String RELEASE_TICKET_URI = "/repos/spring-projects/spring-data-build/issues/233";
	static final String MILESTONES_URI = "/repos/spring-projects/spring-data-build/milestones";
	static final ModuleIteration BUILD_HOPPER_RC1 = ReleaseTrains.HOPPER.getModuleIteration(Projects.BUILD,
			Iteration.RC1);
	static final Train LATEST = ReleaseTrains.latest();

	@RegisterExtension WireMockExtension mockService = new WireMockExtension(
			wireMockConfig().port(8888).fileSource(new ClasspathFileSource("integration/github")));

	@Autowired GitHub github;
	@Autowired GitHubProperties properties;

	@BeforeEach
	void before() {

		UriComponents uriComponents = UriComponentsBuilder.fromUriString(properties.getApiUrl()).build();
		assumeThat(uriComponents.getHost()).isEqualTo("localhost");

		github.reset();
	}

	@Test // #5
	void findTicketsByTicketIds() {

		mockGetIssueWith("issue.json", 233);

		Collection<Ticket> tickets = github.findTickets(LATEST.getSupportedProject(Projects.BUILD),
				Collections
						.singletonList(TicketReference.ofTicket("233", TicketReference.Style.GitHub, GitHubRepository.implicit())));

		assertThat(tickets).hasSize(1);
	}

	@Test // #5
	void ignoresUnknownTicketsByTicketId() {

		Collection<Ticket> tickets = github.findTickets(LATEST.getSupportedProject(Projects.BUILD),
				Collections
						.singletonList(TicketReference.ofTicket("233", TicketReference.Style.GitHub, GitHubRepository.implicit())));
		assertThat(tickets).isEmpty();
	}

	@Test // #5
	void emptyResultWithEmptyTicketIds() {

		Collection<Ticket> tickets = github.findTickets(LATEST.getSupportedProject(Projects.COMMONS),
				Collections.emptyList());
		assertThat(tickets).isEmpty();
	}

	@Test // #5
	void getReleaseTicketForReturnsTheReleaseTicket() {

		mockGetMilestonesWith("milestones.json");
		mockGetIssuesWith("issues.json");

		Ticket releaseTicket = github.getReleaseTicketFor(BUILD_HOPPER_RC1);
		assertThat(releaseTicket.getId()).isEqualTo("#233");
	}

	@Test // #5
	void noMilestoneNoReleaseTicketFound() {

		mockGetMilestonesWith("emptyMilestones.json");

		assertThatIllegalStateException().isThrownBy(() -> github.getReleaseTicketFor(BUILD_HOPPER_RC1))
				.withMessageContaining("No milestone for Spring Data Build found containing 1.8 RC1 (Hopper)!");
	}

	@Test // #5
	void noReleaseTicketFound() {

		mockGetMilestonesWith("milestones.json");
		mockGetIssuesWith("emptyIssues.json");

		assertThatIllegalArgumentException().isThrownBy(() -> github.getReleaseTicketFor(BUILD_HOPPER_RC1))
				.withMessageContaining(
						"Did not find a release ticket for Spring Data Build 1.8 RC1 (Hopper) containing Release 1.8 RC1 (Hopper)!");
	}

	@Test // #5
	void createReleaseVersionShouldCreateAVersion() {

		mockGetMilestonesWith("emptyMilestones.json");
		mockCreateMilestoneWith("milestone.json");

		github.createReleaseVersion(BUILD_HOPPER_RC1);

		verify(postRequestedFor(urlPathMatching(MILESTONES_URI))
				.withRequestBody(equalToJson("{\"title\":\"1.8 RC1 (Hopper)\", \"description\":\"Hopper RC1\"}")));
	}

	@Test // #5
	void createReleaseVersionShouldFindExistingReleaseVersion() {

		mockGetMilestonesWith("milestones.json");

		github.createReleaseVersion(BUILD_HOPPER_RC1);

		verify(0, postRequestedFor(urlPathMatching(MILESTONES_URI)));
	}

	@Test // #5
	void createReleaseTicketShouldCreateReleaseTicket() {

		mockGetMilestonesWith("milestones.json");
		mockGetIssuesWith("emptyIssues.json");
		mockCreateIssueWith("issue.json");

		github.createReleaseTicket(BUILD_HOPPER_RC1);

		verify(postRequestedFor(urlPathMatching(ISSUES_URI)).withRequestBody(equalToJson(
				"{\"title\":\"Release 1.8 RC1 (Hopper)\",\"milestone\":45,\"labels\":[ \"type: task\" ], \"assignees\" : [ ]}")));
	}

	@Test // #5
	void createReleaseTicketShouldFailWithNoReleaseVersion() {

		ModuleIteration moduleIteration = ReleaseTrains.HOPPER.getModuleIteration(Projects.BUILD, Iteration.RC1);

		mockGetIssuesWith("emptyIssues.json");
		mockGetMilestonesWith("emptyMilestones.json");

		assertThatIllegalStateException().isThrownBy(() -> github.createReleaseTicket(moduleIteration))
				.withMessageContaining("No milestone for Spring Data Build found containing 1.8 RC1 (Hopper)!");
	}

	@Test // #5
	void createReleaseTicketShouldFindExistingTicket() {

		mockGetMilestonesWith("milestones.json");
		mockGetIssuesWith("issues.json");

		github.createReleaseTicket(BUILD_HOPPER_RC1);

		verify(0, postRequestedFor(urlPathMatching(MILESTONES_URI)));
	}

	@Test // #55
	void assignTicketToMe() {

		mockGetMilestonesWith("milestones.json");
		mockGetIssuesWith("issues.json");

		mockService.stubFor(patch(urlPathMatching(RELEASE_TICKET_URI)).//
				willReturn(json("issue.json")));

		github.assignReleaseTicketToMe(BUILD_HOPPER_RC1);

		verify(patchRequestedFor(urlPathMatching(RELEASE_TICKET_URI))
				.withRequestBody(equalToJson("{\"assignees\":[\"dummy\"]}")));
	}

	@Test // #94
	void closeIterationShouldResolveReleaseTicket() {

		mockGetMilestonesWith("milestones.json");
		mockGetIssuesWith("issues.json");

		mockService.stubFor(patch(urlPathMatching(RELEASE_TICKET_URI)).//
				willReturn(json("issue.json")));

		mockService.stubFor(patch(urlPathMatching(MILESTONES_URI + "/45")).//
				willReturn(aResponse().withStatus(200)));

		github.closeIteration(BUILD_HOPPER_RC1);

		verify(patchRequestedFor(urlPathMatching(RELEASE_TICKET_URI))
				.withRequestBody(equalToJson("{\"state\":\"closed\",\"assignees\":[\"dummy\"]}")));
	}

	private void mockGetIssueWith(String fromClassPath, int issueId) {
		mockService.stubFor(get(urlPathMatching(ISSUES_URI + "/" + issueId)).//
				willReturn(json(fromClassPath)));
	}

	private void mockGetIssuesWith(String fromClassPath) {
		mockService.stubFor(get(urlPathMatching(ISSUES_URI)).//
				willReturn(json(fromClassPath)));
	}

	private void mockCreateIssueWith(String fromClassPath) {
		mockService.stubFor(post(urlPathMatching(ISSUES_URI)).//
				willReturn(json(fromClassPath)));
	}

	private void mockGetMilestonesWith(String fromClassPath) {
		mockService.stubFor(get(urlPathMatching(MILESTONES_URI)).//
				willReturn(json(fromClassPath)));
	}

	private void mockCreateMilestoneWith(String fromClassPath) {
		mockService.stubFor(post(urlPathMatching(MILESTONES_URI)).//
				willReturn(json(fromClassPath)));
	}

	private ResponseDefinitionBuilder json(String fromClassPathFile) {
		return aResponse().//
				withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).//
				withBodyFile(fromClassPathFile);

	}
}
