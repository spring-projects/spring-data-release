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
package org.springframework.data.release.issues.github;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.release.git.Branch;
import org.springframework.data.release.git.GitProject;
import org.springframework.data.release.git.GitServer;
import org.springframework.data.release.git.Tag;
import org.springframework.data.release.git.VersionTags;
import org.springframework.data.release.issues.Changelog;
import org.springframework.data.release.issues.IssueTracker;
import org.springframework.data.release.issues.Ticket;
import org.springframework.data.release.issues.TicketReference;
import org.springframework.data.release.issues.TicketType;
import org.springframework.data.release.issues.Tickets;
import org.springframework.data.release.issues.github.GitHubWorkflows.GitHubWorkflow;
import org.springframework.data.release.model.*;
import org.springframework.data.release.utils.Logger;
import org.springframework.data.util.Predicates;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Component
public class GitHub extends GitHubSupport implements IssueTracker {

	private static final String MILESTONE_URI = "/repos/{owner}/{repo}/milestones?state={state}&per_page=60";
	private static final String ISSUES_BY_MILESTONE_AND_ASSIGNEE_URI_TEMPLATE = "/repos/spring-projects/{repo}/issues?milestone={id}&state=all&assignee={assignee}";
	private static final String ISSUES_BY_MILESTONE_URI_TEMPLATE = "/repos/{owner}/{repo}/issues?milestone={id}&state=all";
	private static final String MILESTONES_URI_TEMPLATE = "/repos/{owner}/{repo}/milestones";
	private static final String MILESTONE_BY_ID_URI_TEMPLATE = "/repos/{owner}/{repo}/milestones/{id}";
	private static final String ISSUE_BY_ID_URI_TEMPLATE = "/repos/{owner}/{repo}/issues/{id}";
	private static final String ISSUES_URI_TEMPLATE = "/repos/{owner}/{repo}/issues";
	private static final String RELEASE_BY_TAG_URI_TEMPLATE = "/repos/{owner}/{repo}/releases/tags/{tag}";
	private static final String RELEASE_URI_TEMPLATE = "/repos/{owner}/{repo}/releases";
	private static final String RELEASE_BY_ID_URI_TEMPLATE = "/repos/{owner}/{repo}/releases/{id}";

	private static final String WORKFLOWS = "/repos/{owner}/{repo}/actions/workflows";
	private static final String WORKFLOW_DISPATCH = "/repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches";

	private static final ParameterizedTypeReference<List<Milestone>> MILESTONES_TYPE = new ParameterizedTypeReference<List<Milestone>>() {};
	private static final ParameterizedTypeReference<List<GitHubReadIssue>> ISSUES_TYPE = new ParameterizedTypeReference<List<GitHubReadIssue>>() {};
	private static final ParameterizedTypeReference<GitHubReadIssue> ISSUE_TYPE = new ParameterizedTypeReference<GitHubReadIssue>() {};
	private static final ParameterizedTypeReference<GitHubWorkflows> WORKFLOWS_TYPE = new ParameterizedTypeReference<GitHubWorkflows>() {};
	private static final Map<TicketType, Set<Label>> TICKET_LABELS = new HashMap<>();

	private final Map<ModuleIteration, Optional<Milestone>> milestoneCache = new ConcurrentHashMap<>();

	static {

		TICKET_LABELS.put(TicketType.Task, Set.of(LabelConfiguration.TYPE_TASK));
		TICKET_LABELS.put(TicketType.DependencyUpgrade, Set.of(LabelConfiguration.TYPE_DEPENDENCY_UPGRADE));
		TICKET_LABELS.put(TicketType.Enhancement, Set.of(LabelConfiguration.TYPE_ENHANCEMENT));
		TICKET_LABELS.put(TicketType.Bug, Set.of(LabelConfiguration.TYPE_BUG, LabelConfiguration.TYPE_REGRESSION));
	}

	private final Logger logger;
	private final GitHubProperties properties;

	public GitHub(@Qualifier("tracker") RestTemplateBuilder templateBuilder, Logger logger, GitHubProperties properties) {

		super(createOperations(templateBuilder, properties));
		this.logger = logger;
		this.properties = properties;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.jira.JiraConnector#flushTickets()
	 */
	@Override
	@CacheEvict(value = { "tickets", "release-tickets", "milestone" }, allEntries = true)
	public void reset() {
		milestoneCache.clear();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.jira.IssueTracker#getReleaseTicketFor(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	@Cacheable("release-tickets")
	public Ticket getReleaseTicketFor(ModuleIteration module) {
		return getTicketsFor(module).getReleaseTicket(module);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see IssueTracker#findTickets(Project, Collection)
	 */
	@Override
	@Cacheable("tickets")
	public Collection<Ticket> findTickets(SupportedProject project, Collection<TicketReference> ticketIds) {

		List<Ticket> tickets = new ArrayList<>();

		ticketIds.forEach(ticketId -> {

			GitHubReadIssue ticket = findTicket(GitProject.of(project).getRepository(), ticketId.getId());
			if (ticket != null) {
				tickets.add(toTicket(ticket));
			}
		});

		return tickets;
	}

	@Override
	public Tickets findTickets(ModuleIteration moduleIteration, Collection<TicketReference> ticketIds) {

		return findGitHubIssues(moduleIteration, ticketIds).stream().map(ChangeItem::getIssue).map(GitHub::toTicket)
				.collect(Tickets.toTicketsCollector());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.jira.IssueTracker#getChangelogFor(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	@Cacheable("changelogs")
	public Changelog getChangelogFor(ModuleIteration moduleIteration) {

		Tickets tickets = getIssuesFor(moduleIteration, false, false).//
				map(issue -> toTicket(issue)).//
				collect(Tickets.toTicketsCollector());

		logger.log(moduleIteration, "Created changelog with %s entries.", tickets.getOverallTotal());

		return Changelog.of(moduleIteration, tickets);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.plugin.core.Plugin#supports(java.lang.Object)
	 */
	@Override
	public boolean supports(SupportedProject project) {
		return project.getProject().uses(Tracker.GITHUB);
	}

	@Override
	public Tickets getTicketsFor(TrainIteration iteration) {
		return getTicketsFor(iteration, false);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.jira.GitHubIssueConnector#getTicketsFor(org.springframework.data.release.model.TrainIteration, boolean)
	 */
	@Override
	public Tickets getTicketsFor(TrainIteration trainIteration, boolean forCurrentUser) {

		if (forCurrentUser) {
			logger.log(trainIteration, "Retrieving tickets (for user %s)…", properties.getUsername());
		} else {
			logger.log(trainIteration, "Retrieving tickets…");
		}

		return trainIteration.stream(). //
				filter(moduleIteration -> supports(moduleIteration.getSupportedProject())). //
				flatMap(moduleIteration -> getTicketsFor(moduleIteration, forCurrentUser).stream()). //
				collect(Tickets.toTicketsCollector());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.tracker.IssueTracker#createReleaseVersion(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public void createReleaseVersion(ModuleIteration moduleIteration) {

		Assert.notNull(moduleIteration, "ModuleIteration must not be null.");

		Optional<Milestone> milestone = findMilestone(moduleIteration);

		if (milestone.isPresent()) {
			return;
		}

		GithubMilestone githubMilestone = new GithubMilestone(moduleIteration);
		logger.log(moduleIteration, "Creating GitHub milestone %s", githubMilestone);

		HttpHeaders httpHeaders = new HttpHeaders();
		Map<String, Object> parameters = createParameters(moduleIteration);

		ResponseEntity<Milestone> exchange = operations.exchange(MILESTONES_URI_TEMPLATE, HttpMethod.POST,
				new HttpEntity<Object>(githubMilestone.toMilestone(), httpHeaders), Milestone.class, parameters);

		milestoneCache.put(moduleIteration, Optional.ofNullable(exchange.getBody()));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.tracker.IssueTracker#retireReleaseVersion(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public void archiveReleaseVersion(ModuleIteration module) {
		logger.log(module, "Skipping milestone archival");
	}

	/*
	 *
	 * (non-Javadoc)
	 * @see org.springframework.data.release.tracker.IssueTracker#createReleaseTicket(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public void createReleaseTicket(ModuleIteration moduleIteration) {

		Assert.notNull(moduleIteration, "ModuleIteration must not be null.");

		Tickets tickets = getTicketsFor(moduleIteration);
		if (tickets.hasReleaseTicket(moduleIteration)) {
			return;
		}

		logger.log(moduleIteration, "Creating release ticket…");

		doCreateTicket(moduleIteration, Tracker.releaseTicketSummary(moduleIteration), null, TicketType.Task, false);
	}

	@Override
	public Ticket createTicket(ModuleIteration moduleIteration, String subject, String description, TicketType ticketType,
			boolean assignToCurrentUser) {

		logger.log(moduleIteration, "Creating ticket for %s", subject);

		return doCreateTicket(moduleIteration, subject, description, ticketType, assignToCurrentUser);
	}

	private Ticket doCreateTicket(ModuleIteration moduleIteration, String subject, String description,
			TicketType ticketType, boolean assignToCurrentUser) {

		Milestone milestone = getMilestone(moduleIteration);

		Collection<Label> label = TICKET_LABELS.get(ticketType);

		GitHubWriteIssue gitHubIssue = GitHubWriteIssue.of(subject, milestone).withBody(description)
				.withLabel(label.iterator().next().getName());

		if (assignToCurrentUser) {
			gitHubIssue = gitHubIssue.withAssignees(Collections.singletonList(properties.getUsername()));
		}

		Map<String, Object> parameters = createParameters(GitProject.of(moduleIteration));

		GitHubReadIssue body = operations.exchange(ISSUES_URI_TEMPLATE, HttpMethod.POST,
				new HttpEntity<Object>(gitHubIssue), GitHubReadIssue.class, parameters).getBody();

		return toTicket(body);
	}

	@Cacheable("tickets")
	public Tickets getTicketsFor(ModuleIteration iteration) {
		return getTicketsFor(iteration, false);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.issues.IssueTracker#assignTicketToMe(org.springframework.data.release.model.SupportedProject, org.springframework.data.release.issues.Ticket)
	 */
	@Override
	public Ticket assignTicketToMe(SupportedProject project, Ticket ticket) {

		Assert.notNull(ticket, "Ticket must not be null.");

		if (ticket.isAssignedTo(properties.getUsername())) {
			logger.log("Ticket", "Skipping self-assignment of %s", ticket);
			return ticket;
		}

		Map<String, Object> parameters = createParameters(project);
		parameters.put("id", stripHash(ticket));

		GitHubWriteIssue edit = GitHubWriteIssue.assignedTo(properties.getUsername());

		GitHubReadIssue response = operations.exchange(ISSUE_BY_ID_URI_TEMPLATE, HttpMethod.PATCH,
				new HttpEntity<>(edit, new HttpHeaders()), ISSUE_TYPE, parameters).getBody();

		return toTicket(response);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.jira.IssueTracker#assignReleaseTicketToMe(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public Ticket assignReleaseTicketToMe(ModuleIteration module) {

		Assert.notNull(module, "ModuleIteration must not be null.");

		return assignTicketToMe(module.getSupportedProject(), getReleaseTicketFor(module));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.jira.IssueTracker#startReleaseTicketProgress(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public Ticket startReleaseTicketProgress(ModuleIteration module) {
		return getReleaseTicketFor(module);
	}

	/**
	 * Close the release ticket.
	 *
	 * @param module
	 * @return
	 */
	public Ticket closeReleaseTicket(ModuleIteration module) {

		Assert.notNull(module, "ModuleIteration must not be null.");

		Ticket releaseTicketFor = getReleaseTicketFor(module);
		GitHubReadIssue response = close(module, releaseTicketFor);

		return toTicket(response);
	}

	private GitHubReadIssue close(ModuleIteration module, Ticket ticket) {

		String repositoryName = GitProject.of(module).getRepositoryName();

		Map<String, Object> parameters = createParameters(module);
		parameters.put("id", stripHash(ticket));

		GitHubWriteIssue edit = GitHubWriteIssue.assignedTo(properties.getUsername()).close();

		return operations.exchange(ISSUE_BY_ID_URI_TEMPLATE, HttpMethod.PATCH, new HttpEntity<>(edit, new HttpHeaders()),
				ISSUE_TYPE, parameters).getBody();
	}

	private String stripHash(Ticket ticket) {
		return ticket.getId().startsWith("#") ? ticket.getId().substring(1) : ticket.getId();
	}

	private Optional<Milestone> findMilestone(ModuleIteration moduleIteration) {

		// we're inside a cacheable object, so we cannot reuse Spring Caching for inner method calls.
		Optional<Milestone> milestone = milestoneCache.get(moduleIteration);
		if (milestone == null) {

			milestone = doFindMilestone(moduleIteration, GitProject.of(moduleIteration).getRepository(),
					m -> m.matches(moduleIteration));

			if (milestone.isPresent()) {
				milestoneCache.put(moduleIteration, milestone);
			}
		}

		return milestone;
	}

	private Optional<Milestone> doFindMilestone(ModuleIteration moduleIteration, GitHubRepository repository,
			Predicate<Milestone> milestonePredicate) {

		logger.log(moduleIteration, "Looking up milestone…");

		Optional<Milestone> result = doFindMilestone(repository, milestonePredicate);
		result.ifPresent(it -> {
			logger.log(moduleIteration, "Found milestone %s.", it);
		});

		return result;
	}

	private Optional<Milestone> doFindMilestone(GitHubRepository repository, Predicate<Milestone> milestonePredicate) {
		return doFindMilestones(repository, Arrays.asList("open", "closed"), milestonePredicate, m -> false).stream()
				.findFirst();
	}

	private List<Milestone> doFindMilestones(GitHubRepository repository, Collection<String> states,
			Predicate<Milestone> milestonePredicate, Predicate<Milestone> continueOnHitPredicate) {

		List<Milestone> result = new ArrayList<>();

		for (String state : states) {

			Map<String, Object> parameters = createParameters(repository);
			parameters.put("state", state);

			doWithPaging(MILESTONE_URI, HttpMethod.GET, parameters, new HttpEntity<>(new HttpHeaders()), MILESTONES_TYPE,
					milestones -> {
						for (Milestone milestone : milestones) {

							if (milestonePredicate.test(milestone)) {
								result.add(milestone);

								if (!continueOnHitPredicate.test(milestone)) {
									return false;
								}
							}
						}
						return true;
					});
		}

		return result;
	}

	public List<Milestone> listOpenMilestones(SupportStatus supportStatus) {
		GitHubRepository repository = GitProject.of(Projects.RELEASE, supportStatus).getRepository();
		return getOpenMilestones(repository, Predicates.isTrue());
	}

	public List<Milestone> getOpenMilestones(GitHubRepository repository, Predicate<Milestone> filter) {

		logger.log(repository.toString(), "Looking up milestones…");

		List<Milestone> result = doFindMilestones(repository, Collections.singletonList("open"), it -> {

			boolean actualVersion = it.getTitle() != null && !it.getTitle().endsWith(".x")
					&& ArtifactVersion.isVersion(it.getTitle());
			return filter.test(it) && actualVersion;
		}, m -> true);

		Comparator<Instant> date = Comparator.nullsLast(Comparator.comparing(it -> it.truncatedTo(ChronoUnit.DAYS)));

		result.sort(Comparator.comparing(Milestone::getDueOn, date).thenComparing(it -> ArtifactVersion.of(it.getTitle())));
		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.release.issues.IssueTracker#closeIteration(org.springframework.data.release.model.ModuleIteration)
	 */
	@Override
	public void closeIteration(ModuleIteration module) {

		// for each module

		// - close all tickets
		// -- make sure only one ticket is open
		// -- resolve open ticket
		// -- close tickets

		// - mark version as released

		HttpHeaders httpHeaders = new HttpHeaders();

		GitProject project = GitProject.of(module);

		findMilestone(module) //
				.filter(Milestone::isOpen) //
				.map(Milestone::markReleased) //
				.ifPresent(milestone -> {

					logger.log(module, "Marking milestone %s as released.", milestone);

					Map<String, Object> parameters = createParameters(project);
					parameters.put("id", milestone.getNumber());

					operations.exchange(MILESTONE_BY_ID_URI_TEMPLATE, HttpMethod.PATCH,
							new HttpEntity<Object>(milestone, httpHeaders), Map.class, parameters);
				});

		// - if no next version exists, create

		if (getTicketsFor(module).hasReleaseTicket(module)) {
			closeReleaseTicket(module);
		}
	}

	@Override
	public void closeTicket(ModuleIteration module, Ticket ticket) {
		close(module, ticket);
	}

	List<ChangeItem> findGitHubIssues(ModuleIteration moduleIteration, Collection<TicketReference> ticketIds) {

		logger.log(moduleIteration, "Looking up GitHub issues from milestone …");

		Map<String, GitHubReadIssue> issues = getIssuesFor(moduleIteration, false, true)
				.collect(Collectors.toMap(GitHubIssue::getId, Function.identity()));

		GitProject gitProject = GitProject.of(moduleIteration);
		String repositoryName = gitProject.getRepositoryName();
		GitHubRepository repository = gitProject.getRepository();

		logger.log(moduleIteration, "Looking up GitHub issues …");
		Collection<ChangeItem> foundIssues = ticketIds.stream().filter(it -> it.getId().startsWith("#")).flatMap(it -> {

			GitHubRepository repositoryToUse = it.getRepository().isImplicit()
					? repository.mapProjectName(ignore -> repositoryName)
					: it.getRepository();
			GitHubReadIssue ticket = getTicket(issues,
					repositoryToUse, it.getId());

			if (ticket != null) {
				return Stream.of(new ChangeItem(it, ticket));
			}

			return Stream.empty();
		}).collect(Collectors.toList());

		List<ChangeItem> gitHubIssues = foundIssues.stream().filter(it -> {
			Ticket ticket = toTicket(it.getIssue());
			return !ticket.isReleaseTicketFor(moduleIteration) && !ticket.isReleaseTicket();
		}).collect(Collectors.toList());

		logger.log(moduleIteration, "Found %s tickets.", gitHubIssues.size());

		return gitHubIssues;
	}

	private GitHubReadIssue getTicket(Map<String, GitHubReadIssue> cache, GitHubRepository repository, String ticketId) {

		if (cache.containsKey(ticketId)) {
			return cache.get(ticketId);
		}

		return findTicket(repository, ticketId);
	}

	@Override
	public Tickets getTicketsFor(ModuleIteration moduleIteration, boolean forCurrentUser) {

		return getIssuesFor(moduleIteration, forCurrentUser, false)//
				.map(GitHub::toTicket) //
				.collect(Tickets.toTicketsCollector());
	}

	/**
	 * @param repository
	 * @param ticketId
	 * @return
	 */
	private GitHubReadIssue findTicket(GitHubRepository repository, String ticketId) {

		Map<String, Object> parameters = createParameters(repository);
		parameters.put("id", ticketId.startsWith("#") ? ticketId.substring(1) : ticketId);

		try {

			return operations.exchange(ISSUE_BY_ID_URI_TEMPLATE, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
					ISSUE_TYPE, parameters).getBody();
		} catch (HttpStatusCodeException e) {

			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			}

			throw e;
		}
	}

	public void createOrUpdateRelease(ModuleIteration module, List<TicketReference> ticketIds) {

		logger.log(module, "Preparing GitHub Release …");

		String releaseMarkdown = createReleaseMarkdown(module, ticketIds);
		createOrUpdateRelease(module, releaseMarkdown);

		logger.log(module, "GitHub Release up to date");
	}

	public String createReleaseMarkdown(ModuleIteration module, List<TicketReference> ticketIds) {

		List<ChangeItem> gitHubIssues = findGitHubIssues(module, ticketIds);

		ArtifactVersion version = ArtifactVersion.of(module);
		DocumentationMetadata documentation = DocumentationMetadata.of(module, version, false);

		ChangelogGenerator generator = new ChangelogGenerator();
		generator.getExcludeContributors().addAll(properties.getTeam());

		boolean generateLinks = !module.isCommercial();
		String releaseBody = generator.generate(gitHubIssues, (changelogSection, s) -> s, generateLinks);
		String documentationLinks = getDocumentationLinks(module, documentation);

		String releaseMarkdown;
		if (module.getProject() == Projects.BOM || module.getProject() == Projects.BUILD) {

			if (module.getProject() == Projects.BOM) {
				String participatingModules = createParticipatingModules(module.getTrainIteration());

				releaseMarkdown = String.format("## :shipit: Participating Modules%n%n%s%n%s%n", participatingModules,
						releaseBody);
			} else {
				releaseMarkdown = String.format("%s%n", documentationLinks);
			}
		} else {
			releaseMarkdown = String.format("## :green_book: Links%n%s%n%s%n", documentationLinks, releaseBody);
		}
		return releaseMarkdown;
	}

	private String createParticipatingModules(TrainIteration iteration) {

		Comparator<ModuleIteration> comparator = Comparator
				.comparing(moduleIteration -> moduleIteration.getSupportedProject().getName());
		return iteration.stream().sorted(comparator).map(module -> {

			GitProject project = GitProject.of(module);
			Tag tag = VersionTags.empty(module.getProject()).createTag(module);
			return String.format("* [Spring Data %s %s](%s/%s/%s/releases/tag/%s)%n", module.getSupportedProject().getName(),
					tag.getName(), GitServer.INSTANCE.getUri(), project.getOwner(), project.getRepositoryName(), tag.getName());
		}).collect(Collectors.joining());
	}

	/**
	 * Verify GitHub authentication.
	 */
	public void verifyAuthentication(Train train) {

		logger.log("GitHub", "Verifying GitHub Authentication…");

		// /user requires authentication
		ResponseEntity<Object> entity = operations.getForEntity("/user", Object.class);

		if (!entity.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException(String.format("Cannot obtain /user. Status: %s", entity.getStatusCode()));
		}

		logger.log("GitHub", "Authentication verified.");
	}

	/**
	 * Trigger the Antora workflow for the given module.
	 */
	public void triggerAntoraWorkflow(GitHubWorkflow workflow, SupportedProject workflowRepository,
			SupportedProject project) {

		logger.log("GitHub", "Triggering [%s] Antora workflow for %s…", project.getSupportStatus(), project.getName());

		Map<String, Object> parameters = createParameters(GitProject.of(workflowRepository));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ref", "main");
		body.put("inputs", Collections.singletonMap("module", project.getName().toLowerCase(Locale.ROOT)));

		parameters.put("workflow_id", workflow.getId());

		ResponseEntity<Map> entity = operations.exchange(WORKFLOW_DISPATCH, HttpMethod.POST, new HttpEntity<>(body),
				Map.class, parameters);

		if (!entity.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("Cannot trigger [%s] Antora workflow. Status: %s"
					.formatted(project.getSupportStatus(), entity.getStatusCode()));
		}

		logger.log("GitHub", "[%s] Antora workflow for %s started…", project.getSupportStatus(), project.getName());
	}

	public void triggerDownstreamWorkflow(GitHubWorkflow workflow, ModuleIteration moduleIteration) {

		logger.log(moduleIteration, "Triggering workflow '%s'…", workflow.getName());

		Map<String, Object> parameters = createParameters(GitProject.of(moduleIteration.getSupportedProject()));
		Map<String, Object> body = new LinkedHashMap<>();

		body.put("ref", Branch.from(moduleIteration).toString());

		parameters.put("workflow_id", workflow.getId());

		ResponseEntity<Map> entity = operations.exchange(WORKFLOW_DISPATCH, HttpMethod.POST, new HttpEntity<>(body),
				Map.class, parameters);

		if (!entity.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException(
					"Cannot trigger workflow '%s'. Status: %s".formatted(moduleIteration.getProject(), entity.getStatusCode()));
		}

		logger.log(moduleIteration, "Workflow '%s' started.", workflow.getName());
	}

	@Cacheable("get-workflow")
	public GitHubWorkflow getAntoraWorkflow(SupportedProject workflowRepository, SupportStatus supportStatus) {

		logger.log(workflowRepository, "Resolving Antora workflow…");

		return doGetWorkflow(workflowRepository, workflow -> {

			if (supportStatus.isOpenSource() && workflow.getPath().endsWith("antora-oss-site.yml")) {
				return true;
			}

			if (supportStatus.isCommercial() && workflow.getPath().endsWith("antora-commercial-site.yml")) {
				return true;
			}

			return false;

		}).orElseThrow(
				() -> new NoSuchElementException("Cannot resolve Antora workflow for with status %s".formatted(supportStatus)));
	}

	@Cacheable("find-project-workflow")
	public GitHubWorkflow getWorkflow(SupportedProject project, String workflowName) {

		logger.log(project, "Resolving workflow '%s'…", workflowName);

		return doGetWorkflow(project,
				workflow -> workflow.getPath().endsWith(workflowName) || workflow.getName().equals(workflowName))
				.orElseThrow(() -> new NoSuchElementException(
						"Cannot resolve workflow '%s' for project %s".formatted(workflowName, project)));
	}

	public Optional<GitHubWorkflow> doGetWorkflow(SupportedProject project, Predicate<GitHubWorkflow> predicate) {

		logger.log(project, "Resolving workflow…");

		GitHubWorkflows workflows = getGitHubWorkflows(project);

		for (GitHubWorkflow workflow : workflows.getWorkflows()) {

			if (predicate.test(workflow)) {

				if (!workflow.getState().equals("active")) {
					throw new IllegalStateException("Workflow '%s' is not active".formatted(workflow));
				}

				return Optional.of(workflow);
			}
		}

		return Optional.empty();
	}

	private GitHubWorkflows getGitHubWorkflows(SupportedProject project) {

		Map<String, Object> parameters = createParameters(GitProject.of(project));
		ResponseEntity<GitHubWorkflows> entity = operations.exchange(WORKFLOWS, HttpMethod.GET, null, WORKFLOWS_TYPE,
				parameters);

		if (!entity.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException(String.format("Cannot obtain Workflows. Status: %s", entity.getStatusCode()));
		}

		return entity.getBody();
	}

	private String getDocumentationLinks(ModuleIteration module, DocumentationMetadata documentation) {

		if (module.getProject() == Projects.BUILD || module.getProject() == Projects.BOM) {
			return "";
		}

		String referenceDocUrl = documentation.getReferenceDocUrl();
		String apiDocUrl = documentation.getApiDocUrl();

		String reference = String.format("* [%s %s Reference documentation](%s)", module.getProject().getFullName(),
				module.getVersion().toString(), referenceDocUrl);

		String apidoc = String.format("* [%s %s Javadoc](%s)", module.getProject().getFullName(),
				module.getVersion().toString(), apiDocUrl);

		return String.format("%s%n%s%n", reference, apidoc);
	}

	private void createOrUpdateRelease(ModuleIteration module, String body) {

		GitProject gitProject = GitProject.of(module);
		Tag tag = VersionTags.empty(module.getProject()).createTag(module);
		logger.log(module, "Looking up GitHub Release …");

		Iteration iteration = module.getTrainIteration().getIteration();
		boolean prerelase = iteration.isPreview();
		GitHubRelease release = findRelease(gitProject, tag.getName());

		if (release == null) {
			release = new GitHubRelease(null, tag.getName(), tag.getName(), body, false, prerelase);
			logger.log(module, "Creating new Release …");
			createRelease(gitProject, release);
		} else {
			release = release.withPrerelease(prerelase).withBody(body);
			logger.log(module, "Updating new Release …");
			updateRelease(gitProject, release);
		}
	}

	private GitHubRelease findRelease(GitProject gitProject, String tagName) {

		Map<String, Object> parameters = createParameters(gitProject);
		parameters.put("tag", tagName);

		try {
			return operations.exchange(RELEASE_BY_TAG_URI_TEMPLATE, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
					GitHubRelease.class, parameters).getBody();
		} catch (HttpStatusCodeException e) {

			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			}

			throw e;
		}
	}

	private void createRelease(GitProject gitProject, GitHubRelease release) {

		Map<String, Object> parameters = createParameters(gitProject);

		operations.exchange(RELEASE_URI_TEMPLATE, HttpMethod.POST, new HttpEntity<>(release), GitHubRelease.class,
				parameters);
	}

	private void updateRelease(GitProject gitProject, GitHubRelease release) {

		Map<String, Object> parameters = createParameters(gitProject);
		parameters.put("id", release.getId());

		operations.exchange(RELEASE_BY_ID_URI_TEMPLATE, HttpMethod.PATCH, new HttpEntity<>(release), Map.class, parameters);
	}

	private Stream<GitHubReadIssue> getIssuesFor(ModuleIteration moduleIteration, boolean forCurrentUser,
			boolean ignoreMissingMilestone) {

		GitProject gitProject = GitProject.of(moduleIteration);
		Optional<Milestone> optionalMilestone = findMilestone(moduleIteration);

		if (ignoreMissingMilestone && optionalMilestone.isEmpty()) {
			return Stream.empty();
		}

		Milestone milestone = optionalMilestone.orElseThrow(() -> noSuchMilestone(moduleIteration));

		Map<String, Object> parameters = createParameters(gitProject);
		parameters.put("id", milestone.getNumber());

		if (forCurrentUser) {
			parameters.put("assignee", properties.getUsername());
			return getForIssues(ISSUES_BY_MILESTONE_AND_ASSIGNEE_URI_TEMPLATE, parameters);
		}

		return getForIssues(ISSUES_BY_MILESTONE_URI_TEMPLATE, parameters);
	}

	private Stream<GitHubReadIssue> getForIssues(String template, Map<String, Object> parameters) {

		List<GitHubReadIssue> issues = new ArrayList<>();
		doWithPaging(template, HttpMethod.GET, parameters, new HttpEntity<>(new HttpHeaders()), ISSUES_TYPE, tickets -> {
			issues.addAll(tickets);
			return true;
		});

		return issues.stream();
	}

	private Milestone getMilestone(ModuleIteration moduleIteration) {
		Optional<Milestone> milestone = findMilestone(moduleIteration);
		return milestone.orElseThrow(() -> noSuchMilestone(moduleIteration));
	}

	private IllegalStateException noSuchMilestone(ModuleIteration moduleIteration) {
		return new IllegalStateException(String.format("No milestone for %s found containing %s!", //
				moduleIteration.getProject().getFullName(), //
				new GithubMilestone(moduleIteration)));
	}

	private static Ticket toTicket(GitHubReadIssue issue) {

		EnumSet<TicketType> ticketTypes = EnumSet.noneOf(TicketType.class);
		for (Label label : issue.getLabels()) {
			TICKET_LABELS.forEach((k, v) -> {
				if (v.contains(label)) {
					ticketTypes.add(k);
				}
			});
		}

		return new Ticket(issue.getId(), issue.getTitle(), issue.getUrl(),
				issue.getAssignees().isEmpty() ? null : issue.getAssignees().get(0), new GithubTicketStatus(issue.getState()),
				ticketTypes);
	}

	private static Map<String, Object> createParameters(ProjectAware projectAware) {
		return createParameters(GitProject.of(projectAware.getSupportedProject()));
	}

	private static Map<String, Object> createParameters(GitProject gitProject) {
		return createParameters(gitProject.getRepository());
	}

	private static Map<String, Object> createParameters(GitHubRepository repository) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("owner", repository.getOwner());
		parameters.put("repo", repository.getRepositoryName());
		return parameters;
	}

	/**
	 * Resolve the train iteration (M1 or SR1 along with the train) from a given {@link SupportedProject} and branch
	 * reference (e.g. "head/main" or "head/2.7.x").
	 *
	 * @param sourceProject
	 * @param branchRef
	 * @return
	 */
	public TrainIteration resolveTrainIteration(SupportedProject sourceProject, String branchRef) {

		Branch branch = Branch.from(branchRef);
		Train train;
		TrainIteration iteration;
		if (branch.isServiceReleaseBranch()) {
			train = ReleaseTrains.getByProjectVersion(sourceProject.getProject(), branch.asVersion());
			branch.asVersion();
			return train.getIteration(Iteration.SR1);
		} else if (branch.isMainBranch()) {
			train = ReleaseTrains.latest();
			return train.getIteration(Iteration.M1);
		}

		throw new IllegalArgumentException(
				"Cannot determine TrainIteration from project %s and branch '%s' for triggering downstream workflow"
						.formatted(sourceProject, branchRef));
	}
}
