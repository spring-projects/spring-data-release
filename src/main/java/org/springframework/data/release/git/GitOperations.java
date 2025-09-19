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
package org.springframework.data.release.git;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialItem.CharArrayType;
import org.eclipse.jgit.transport.CredentialItem.InformationalMessage;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;

import org.springframework.data.release.io.Workspace;
import org.springframework.data.release.issues.IssueTracker;
import org.springframework.data.release.issues.Ticket;
import org.springframework.data.release.issues.TicketReference;
import org.springframework.data.release.issues.TicketStatus;
import org.springframework.data.release.issues.github.GitHubRepository;
import org.springframework.data.release.model.*;
import org.springframework.data.release.utils.ExecutionUtils;
import org.springframework.data.release.utils.Logger;
import org.springframework.data.util.Pair;
import org.springframework.data.util.Streamable;
import org.springframework.lang.Nullable;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Component to execute Git related operations.
 *
 * @author Oliver Gierke
 * @author Mark Paluch
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GitOperations {

	private enum BranchCheckoutMode {
		CREATE_ONLY, CREATE_AND_UPDATE;
	}

	GitServer server = new GitServer();
	Executor executor;
	Workspace workspace;
	Logger logger;
	PluginRegistry<IssueTracker, SupportedProject> issueTracker;
	GitProperties gitProperties;
	Gpg gpg;

	/**
	 * Resets the repositories for all modules of the given {@link Train}.
	 *
	 * @param train must not be {@literal null}.
	 */
	public void reset(TrainIteration train) {

		Assert.notNull(train, "Train must not be null!");

		ExecutionUtils.run(executor, train, module -> {
			reset(module.getSupportedProject(), Branch.from(module));
		});
	}

	public void checkout(Train train) {
		checkout(train, true);
	}

	/**
	 * Checks out all projects of the given {@link Train}.
	 *
	 * @param train
	 * @param update whether to fetch an update from origin.
	 */
	public void checkout(Train train, boolean update) {
		checkout(train, update, true);
	}

	/**
	 * Checks out all projects of the given {@link Train}.
	 *
	 * @param train
	 * @param update whether to fetch an update from origin.
	 * @param reset whether to reset HARD.
	 */
	public void checkout(Train train, boolean update, boolean reset) {

		Assert.notNull(train, "Train must not be null!");

		if (update) {
			update(train);
		}

		ExecutionUtils.run(executor, train.getModules(), module -> {

			SupportedProject project = train.getSupportedProject(module);

			doWithGit(project, git -> {

				Branch branch = getBranch(train, module, project);
				checkoutBranch(project, git, branch);

				if (reset) {
					reset(project, branch);
				}
			});
		});

		logger.log(train, "Successfully checked out projects.");

	}

	private Branch getBranch(Train train, org.springframework.data.release.model.Module module,
			SupportedProject project) {

		ModuleIteration gaIteration = train.getModuleIteration(project.getProject(), Iteration.GA);
		Optional<Tag> gaTag = findTagFor(project, ArtifactVersion.of(gaIteration));

		if (gaTag.isEmpty()) {
			logger.log(project, "Checking out main branch as no GA release tag could be found!");
		}

		return gaTag.isPresent() ? Branch.from(module) : Branch.MAIN;
	}

	private void checkoutBranch(SupportedProject project, Git git, Branch branch) throws GitAPIException {

		CheckoutCommand command = git.checkout().setName(branch.toString()).setForced(true);

		if (!branchExists(project, branch)) {

			logger.log(project, "git checkout -b %s --track origin/%s", branch, branch);
			command.setCreateBranch(true)//
					.setStartPoint("origin/".concat(branch.toString()))//
					.call();
		} else {

			logger.log(project, "git checkout %s", branch);
			command.call();
		}
	}

	/**
	 * Checks out all projects of the given {@link TrainIteration} using their tags.
	 *
	 * @param iteration must not be {@literal null}.
	 */
	public void checkout(TrainIteration iteration) {

		Assert.notNull(iteration, "Train iteration must not be null!");

		ExecutionUtils.run(executor, iteration, module -> {

			SupportedProject project = module.getSupportedProject();
			ArtifactVersion artifactVersion = ArtifactVersion.of(module);

			Tag tag = findTagFor(project, artifactVersion).orElseThrow(() -> new IllegalStateException(
					String.format("No tag found for version %s of project %s, aborting.", artifactVersion, project)));

			doWithGit(project, git -> {

				logger.log(module, "git checkout %s", tag);
				git.checkout().setName(tag.toString()).setForced(true).call();
			});
		});

		logger.log(iteration, "Successfully checked out projects.");
	}

	public void prepare(TrainIteration iteration) {

		ExecutionUtils.run(executor, iteration, module -> {

			SupportedProject project = module.getSupportedProject();
			Branch branch = Branch.from(module);

			update(project, branch);
			checkout(project, branch);

			logger.log(project, "Pulling latest updates for branch %s…", branch);

			doWithGit(project, git -> {

				logger.log(project, "git pull origin %s", branch);

				call(git.pull().setRebase(true));
			});

			logger.log(project, "Pulling updates done!", branch);
		});

		reset(iteration);
	}

	public void update(Train train) {
		ExecutionUtils.run(executor, train, this::update);
	}

	public void push(TrainIteration iteration) {
		ExecutionUtils.run(executor, iteration, this::push);
	}

	public void push(ModuleIteration module) {

		Branch branch = Branch.from(module);
		logger.log(module, "git push origin %s", branch);

		SupportedProject project = module.getSupportedProject();

		if (!branchExists(project, branch)) {

			logger.log(module, "No branch %s in %s, skip push", branch, project.getName());
			return;
		}

		doWithGit(project, git -> {

			Ref ref = git.getRepository().findRef(branch.toString());

			call(git.push() //
					.setRemote("origin") //
					.setRefSpecs(new RefSpec(ref.getName()))).forEach(pushResult -> {
						handlePushResult(module, pushResult);
					});
		});
	}

	private void handlePushResult(ModuleIteration module, PushResult pushResult) {

		Set<RemoteRefUpdate.Status> success = new HashSet<>(Arrays.asList(RemoteRefUpdate.Status.AWAITING_REPORT,
				RemoteRefUpdate.Status.NOT_ATTEMPTED, RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE));

		if (StringUtils.hasText(pushResult.getMessages())) {
			logger.log(module, pushResult.getMessages());
		}

		for (RemoteRefUpdate remoteUpdate : pushResult.getRemoteUpdates()) {

			if (success.contains(remoteUpdate.getStatus())) {

				logger.log(module.getProject().getName(), String.format("✅️ Push done: %s %s", getMessage(remoteUpdate),
						StringUtils.hasText(remoteUpdate.getMessage()) ? remoteUpdate.getMessage() : ""));

				continue;
			}

			logger.warn(module.getProject().getName(),
					String.format("⚠️ Push failed: %s %s", getMessage(remoteUpdate),
							StringUtils.hasText(remoteUpdate.getMessage()) ? remoteUpdate.getMessage() : ""));
		}
	}

	private static String getMessage(RemoteRefUpdate remoteUpdate) {

		RemoteRefUpdate.Status status = remoteUpdate.getStatus();

		return switch (status) {
			case UP_TO_DATE -> "Branch up-to-date";
			case REJECTED_REMOTE_CHANGED -> "Remote branch changed";
			case NON_EXISTING -> "Remote branch does not exist";
			case AWAITING_REPORT -> "Awaiting report…";
			default -> status.name();
		};
	}

	public void pushTags(Train train) {

		ExecutionUtils.run(executor, train.getModules(), module -> {

			SupportedProject project = train.getSupportedProject(module);

			logger.log(project, "git push --tags origin");

			doWithGit(project, git -> {

				call(git.push() //
						.setRemote("origin") //
						.setPushTags());
			});
		});
	}

	/**
	 * Updates the given {@link Project}. Will either pull the latest changes or clone the project's repository if not
	 * already available.
	 *
	 * @param project must not be {@literal null}.
	 */
	public void update(SupportedProject project) {
		update(project, null);
	}

	/**
	 * Updates the given {@link Project}. Will either pull the latest changes or clone the project's repository if not
	 * already available.
	 *
	 * @param project must not be {@literal null}.
	 * @param branch
	 */
	@SneakyThrows
	public void update(SupportedProject project, @Nullable Branch branch) {

		Assert.notNull(project, "Project must not be null!");

		logger.log(project, "Updating project…");

		GitProject gitProject = getGitProject(project);
		String repositoryName = gitProject.getRepositoryName();

		if (workspace.hasProjectDirectory(project)) {

			doWithGit(gitProject.getProject(), git -> {

				logger.log(project, "Found existing repository %s. Obtaining latest changes…", repositoryName);
				checkout(project, branch == null ? Branch.from(git.getRepository().getBranch()) : branch);

				logger.log(project, "git fetch --tags");
				call(git.fetch().setTagOpt(TagOpt.FETCH_TAGS));

			});
		} else {
			clone(gitProject);
		}

		logger.log(project, "Project update done!");
	}

	/**
	 * Updates the given {@link Project} by fetching all tags.
	 *
	 * @param project must not be {@literal null}.
	 */
	@SneakyThrows
	public void fetchTags(Project project, Train train) {

		Assert.notNull(project, "Project must not be null!");

		logger.log(project, "Updating project tags…");

		SupportedProject supportedProject = train.getSupportedProject(project);
		GitProject gitProject = getGitProject(supportedProject);
		String repositoryName = gitProject.getRepositoryName();

		if (workspace.hasProjectDirectory(supportedProject)) {

			doWithGit(gitProject.getProject(), git -> {

				logger.log(project, "Found existing repository %s. Obtaining tags…", repositoryName);
				logger.log(project, "git fetch --tags");

				call(git.fetch() //
						.setTagOpt(TagOpt.FETCH_TAGS));

			});
		} else {
			clone(gitProject);
		}

		logger.log(project, "Project tags update done!");
	}

	private GitProject getGitProject(SupportedProject project) {
		return new GitProject(project, server);
	}

	public VersionTags getTags(SupportedProject project) {

		return doWithGit(project, git -> {

			git.tagList().call();

			return new VersionTags(project.getProject(), git.tagList().call().stream()//
					.map(ref -> {

						RevCommit commit = getCommit(git.getRepository(), ref);

						PersonIdent authorIdent = commit.getAuthorIdent();
						Date authorDate = authorIdent.getWhen();
						TimeZone authorTimeZone = authorIdent.getTimeZone();
						LocalDateTime localDate = authorDate.toInstant().atZone(authorTimeZone.toZoneId()).toLocalDateTime();

						return Tag.of(ref.getName(), localDate);
					})//
					.collect(Collectors.toList()));
		});
	}

	private RevCommit getCommit(Repository repository, Ref ref) {

		return doWithGit(repository, git -> {

			Ref peeledRef = git.getRepository().getRefDatabase().peel(ref);
			LogCommand log = git.log();
			if (peeledRef.getPeeledObjectId() != null) {
				log.add(peeledRef.getPeeledObjectId());
			} else {
				log.add(ref.getObjectId());
			}

			return Streamable.of(log.call()).stream().findFirst()
					.orElseThrow(() -> new IllegalStateException("Cannot resolve commit for " + ref));
		});
	}

	/**
	 * Retrieve a list of remote branches where their related ticket is resolved.
	 *
	 * @param project must not be {@literal null}.
	 * @return
	 */
	public TicketBranches listTicketBranches(SupportedProject project) {

		Assert.notNull(project, "Project must not be null!");

		IssueTracker tracker = issueTracker.getRequiredPluginFor(project,
				() -> String.format("No issue tracker found for project %s!", project));

		return doWithGit(project, git -> {

			update(project);

			Map<String, Branch> ticketIds = getRemoteBranches(project)//
					.filter(branch -> branch.isIssueBranch(project.getProject().getTracker()))//
					.collect(Collectors.toMap(Branch::toString, branch -> branch));

			Collection<Ticket> tickets = tracker.findTickets(project, ticketIds.keySet().stream()
					.map(it -> TicketReference.ofTicket(it, TicketReference.Style.GitHub, GitHubRepository.implicit()))
					.collect(Collectors.toList()));

			return TicketBranches
					.from(tickets.stream().collect(Collectors.toMap(ticket -> ticketIds.get(ticket.getId()), ticket -> ticket)));
		});
	}

	/**
	 * Lookup the previous {@link TrainIteration} from existing tags.
	 *
	 * @param trainIteration must not be {@literal null}.
	 * @return
	 * @throws IllegalStateException if no previous iteration could be found.
	 */
	public TrainIteration getPreviousIteration(TrainIteration trainIteration) {

		Assert.notNull(trainIteration, "TrainIteration must not be null!");

		if (isGaOrFirstMilestone(trainIteration.getIteration())) {

			Train trainToUse = getPreviousTrain(trainIteration);
			return trainToUse.getIteration(Iteration.GA);
		}

		Iteration iteration = trainIteration.getIteration();
		Train train = trainIteration.getTrain();
		if (iteration.isServiceIteration()) {
			return train.getIteration(iteration.getPrevious());
		}

		SupportedProject build = trainIteration.getSupportedProject(Projects.BUILD);

		Optional<TrainIteration> mostRecentBefore = getTags(build) //
				.filter((tag, ti) -> ti.getTrain().equals(train)) //
				.find((tag, ti) -> ti.getIteration().compareTo(trainIteration.getIteration()) < 0, Pair::getSecond);

		return mostRecentBefore.orElseThrow(() -> new IllegalStateException(
				"Cannot determine previous iteration for " + trainIteration.getReleaseTrainNameAndVersion()));
	}

	public List<TicketReference> getTicketReferencesBetween(SupportedProject project, TrainIteration from,
			TrainIteration to) {

		VersionTags tags = getTags(project);
		List<TicketReference> ticketReferences = doWithGit(project, git -> {

			Repository repo = git.getRepository();

			ModuleIteration toModuleIteration = to.getModule(project.getProject());
			ObjectId fromTag = resolveLowerBoundary(project.getStatus(), project.getProject(), from, tags, git, repo);
			ObjectId toTag = resolveUpperBoundary(toModuleIteration, tags, repo);

			Iterable<RevCommit> commits = git.log().addRange(fromTag, toTag).call();

			return StreamSupport.stream(commits.spliterator(), false).flatMap(it -> {

				ParsedCommitMessage message = ParsedCommitMessage.parse(it.getFullMessage());

				if (message.getTicketReference() == null) {
					logger.warn(toModuleIteration, "Commit %s does not refer to a ticket (%s)", it.getName(),
							it.getShortMessage());
					return Stream.empty();
				}

				return message.getTicketReferences().stream();

			}).collect(Collectors.toList());
		});

		return getUniqueTicketReferences(ticketReferences);
	}

	static List<TicketReference> getUniqueTicketReferences(List<TicketReference> ticketReferences) {

		// make TicketReference unique
		MultiValueMap<String, TicketReference> collated = new LinkedMultiValueMap<>();
		List<TicketReference> uniqueTicketReferences = new ArrayList<>();

		for (TicketReference reference : ticketReferences) {
			collated.add(reference.getRepository() + reference.getId(), reference);
		}

		for (Map.Entry<String, List<TicketReference>> entry : collated.entrySet()) {
			uniqueTicketReferences.add(getTicketReference(entry));
		}

		uniqueTicketReferences.sort(Comparator.<TicketReference> naturalOrder().reversed());

		return uniqueTicketReferences;
	}

	private static TicketReference getTicketReference(Map.Entry<String, List<TicketReference>> entry) {

		for (TicketReference ticketReference : entry.getValue()) {

			if (ticketReference.isIssue()) {
				return ticketReference;
			}
		}

		return entry.getValue().get(0);
	}

	protected ObjectId resolveLowerBoundary(SupportStatus supportStatus, Project project, TrainIteration iteration,
			VersionTags tags, Git git, Repository repo) throws IOException, GitAPIException {

		if (iteration.contains(project)) {

			Iteration it = iteration.getIteration();

			if (iteration.getTrain().isAlwaysUseBranch()) {

				Branch from = Branch.from(iteration.getModule(project));
				String message = expandSummary("Release version %s", iteration.getModule(project), iteration);

				RevCommit releaseCommit = findCommit(git, from, message);
				if (releaseCommit != null) {
					return releaseCommit;
				}
			}

			Optional<Tag> fromTag = tags.filter(iteration.getTrain()).findTag(it);

			if (fromTag.isEmpty()) {

				// commercial releases might not have a previous tag as commercial releases are seeded without OSS tags.
				if (supportStatus == SupportStatus.COMMERCIAL && (it.isServiceIteration() || it.isGAIteration())) {

					Branch from = Branch.from(iteration.getModule(project));
					String message = "Seed";

					RevCommit first = findCommit(git, from, message);
					if (first != null) {
						return first;
					}
				}

				// fall back to main
				return repo.parseCommit(repo.resolve(Branch.MAIN.toString()));
			}

			Tag tag = fromTag.orElseThrow(() -> new IllegalStateException(
					String.format("Cannot determine from tag for %s %s", project.getName(), iteration)));

			return repo.parseCommit(repo.resolve(tag.getName()));
		}

		return repo.resolve(getFirstCommit(repo));
	}

	private static RevCommit findCommit(Git git, Branch branch, String message) throws GitAPIException, IOException {

		String branchRef = branch.withRemote(git.getRepository()).toString();
		Iterable<RevCommit> commits = git.log().add(git.getRepository().resolve(branchRef)).call();

		Optional<RevCommit> first = Streamable.of(commits).stream().filter(rev -> rev.getFullMessage().contains(message))
				.findFirst();

		return first.orElse(null);
	}

	protected ObjectId resolveUpperBoundary(ModuleIteration iteration, VersionTags tags, Repository repo)
			throws IOException {

		Optional<Tag> tag = tags.filter(iteration.getTrain()).findTag(iteration.getIteration());
		String rangeEnd = tag.map(Tag::getName).orElse(Branch.from(iteration).withRemote(repo).toString());
		ObjectId resolve = repo.resolve(rangeEnd);

		if (resolve == null) {
			throw new IllegalStateException(String.format("Cannot resolve %s for %s", rangeEnd, iteration));
		}
		return repo.parseCommit(resolve);
	}

	private static String getFirstCommit(Repository repo) throws IOException {
		return getFirstCommit(repo, Branch.MAIN);
	}

	private static String getFirstCommit(Repository repo, Branch branch) throws IOException {

		try (RevWalk revWalk = new RevWalk(repo)) {
			return revWalk.parseCommit(repo.resolve(branch.withRemote(repo).toString())).getName();
		}
	}

	private static Train getPreviousTrain(TrainIteration trainIteration) {

		Train trainToUse = ReleaseTrains.CODD;

		for (Train train : ReleaseTrains.trains()) {
			if (train.isBefore(trainIteration.getTrain())) {
				trainToUse = train;
			} else {
				break;
			}
		}
		return trainToUse;
	}

	private static boolean isGaOrFirstMilestone(Iteration iteration) {
		return iteration.isGAIteration() || iteration.isMilestone() && iteration.getIterationValue() == 1;
	}

	private Stream<Branch> getRemoteBranches(SupportedProject project) {

		return doWithGit(project, git -> {

			Collection<Ref> refs = git.lsRemote()//
					.setHeads(true)//
					.setTags(false)//
					.call();

			return refs.stream()//
					.map(Ref::getName)//
					.map(Branch::from);//
		});
	}

	/**
	 * Tags the release commits for the given {@link TrainIteration}.
	 *
	 * @param iteration
	 */
	public void tagRelease(TrainIteration iteration) {

		Assert.notNull(iteration, "Train iteration must not be null!");

		ExecutionUtils.run(executor, iteration, module -> {

			SupportedProject project = module.getSupportedProject();
			ObjectId hash = getReleaseHash(module);
			Tag tag = getTags(project).createTag(module);

			doWithGit(project, git -> {

				try (RevWalk walk = new RevWalk(git.getRepository())) {

					RevCommit commit = walk.parseCommit(hash);

					logger.log(module, "git tag %s %s", tag, hash.getName());
					git.tag().setName(tag.toString()).setObjectId(commit).call();
				}
			});
		});
	}

	/**
	 * Commits all changes currently made to all modules of the given {@link TrainIteration}. The summary can contain a
	 * single {@code %s} placeholder which the version of the current module will get replace into.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param summary must not be {@literal null} or empty.
	 * @throws Exception
	 */
	public void commit(TrainIteration iteration, String summary) {
		commit(iteration, summary, Optional.empty());
	}

	/**
	 * Commits all changes currently made to all modules of the given {@link TrainIteration}. The summary can contain a
	 * single {@code %s} placeholder which the version of the current module will get replace into.
	 *
	 * @param iteration must not be {@literal null}.
	 * @param summary must not be {@literal null} or empty.
	 * @param details can be {@literal null} or empty.
	 */
	public void commit(TrainIteration iteration, String summary, Optional<String> details) {

		Assert.notNull(iteration, "Train iteration must not be null!");
		Assert.hasText(summary, "Summary must not be null or empty!");

		ExecutionUtils.run(executor, iteration,
				module -> commit(module, expandSummary(summary, module, iteration), details));
	}

	/**
	 * Commits the given files for the given {@link ModuleIteration} using the given summary for the commit message. If no
	 * files are given, all pending changes are committed.
	 *
	 * @param module must not be {@literal null}.
	 * @param summary must not be {@literal null} or empty.
	 */
	public void commit(ModuleIteration module, String summary) {
		commit(module, summary, Optional.empty());
	}

	/**
	 * Commits the given files for the given {@link ModuleIteration} using the given summary and details for the commit
	 * message. If no files are given, all pending changes are committed.
	 *
	 * @param module must not be {@literal null}.
	 * @param summary must not be {@literal null} or empty.
	 * @param details can be {@literal null} or empty.
	 */
	public void commit(ModuleIteration module, String summary, Optional<String> details) {

		Assert.notNull(module, "Module iteration must not be null!");
		Assert.hasText(summary, "Summary must not be null or empty!");

		SupportedProject project = module.getSupportedProject();
		IssueTracker tracker = issueTracker.getRequiredPluginFor(project,
				() -> String.format("No issue tracker found for project %s!", project));
		Ticket ticket = tracker.getReleaseTicketFor(module);

		commit(module, ticket, summary, details, true);
	}

	/**
	 * Commits the given files for the given {@link ModuleIteration} using the given summary and details for the commit
	 * message. If no files are given, all pending changes are committed.
	 *
	 * @param module must not be {@literal null}.
	 * @param summary must not be {@literal null} or empty.
	 * @param details can be {@literal null} or empty.
	 */
	public void commit(ModuleIteration module, String summary, Optional<String> details, boolean all) {

		Assert.notNull(module, "Module iteration must not be null!");
		Assert.hasText(summary, "Summary must not be null or empty!");

		SupportedProject project = module.getSupportedProject();
		IssueTracker tracker = issueTracker.getRequiredPluginFor(project,
				() -> String.format("No issue tracker found for project %s!", project));
		Ticket ticket = tracker.getReleaseTicketFor(module);

		commit(module, ticket, summary, details, all);
	}

	/**
	 * Commits the given files for the given {@link ModuleIteration} using the given summary and details for the commit
	 * message. If no files are given, all pending changes are committed.
	 *
	 * @param module must not be {@literal null}.
	 * @param summary must not be {@literal null} or empty.
	 * @param details can be {@literal null} or empty.
	 */
	public void commit(ProjectAware module, Ticket ticket, String summary, Optional<String> details, boolean all) {

		Assert.notNull(module, "ProjectAware must not be null!");

		commit(module.getSupportedProject(), ticket, summary, details, all);
	}

	/**
	 * Commits the given files for the given {@link ModuleIteration} using the given summary and details for the commit
	 * message. If no files are given, all pending changes are committed.
	 *
	 * @param project must not be {@literal null}.
	 * @param summary must not be {@literal null} or empty.
	 * @param details can be {@literal null} or empty.
	 */
	public void commit(SupportedProject project, Ticket ticket, String summary, Optional<String> details, boolean all) {

		Assert.notNull(project, "Project must not be null!");
		Assert.hasText(summary, "Summary must not be null or empty!");

		Commit commit = new Commit(ticket, summary, details);
		String author = gitProperties.getAuthor();
		String email = gitProperties.getEmail();
		boolean allowEmpty = all;

		Gpg gpg = getGpg();

		logger.log(project, "git commit -m \"%s\" %s --author=\"%s <%s>\"", commit.getSummary(),
				gpg.isGpgAvailable() ? "-S" + gpg.getKeyname() : "", author, email);

		doWithGit(project, git -> {

			CommitCommand commitCommand = git.commit()//
					.setMessage(commit.toString())//
					.setAuthor(author, email)//
					.setCommitter(author, email)//
					.setAllowEmpty(allowEmpty) //
					.setAll(all);

			if (gpg.isGpgAvailable()) {
				commitCommand //
						.setSign(true) //
						.setSigningKey(gpg.getKeyname()) //
						.setCredentialsProvider(new GpgPassphraseProvider(gpg));
			} else {
				commitCommand.setSign(false);
			}

			try {
				commitCommand.call();
			} catch (EmptyCommitException e) {
				// allowed if not all
			}
		});
	}

	/**
	 * Adds the {@code filepattern} to the staging area.
	 *
	 * @param project must not be {@literal null}.
	 * @param filepattern must not be {@literal null} or empty.
	 */
	public void add(SupportedProject project, String filepattern) {

		Assert.notNull(project, "Project must not be null!");

		logger.log(project, "git add \"%s\"", filepattern);

		doWithGit(project, git -> {

			AddCommand commitCommand = git.add()//
					.addFilepattern(filepattern);

			commitCommand.call();
		});
	}

	/**
	 * Checks out the given {@link Branch} of the given {@link SupportedProject}. If the given branch doesn't exist yet, a
	 * tracking branch is created assuming the branch exists in the {@code origin} remote. Pulls the latest changes from
	 * the checked out branch will be pulled to make sure we see them.
	 *
	 * @param project must not be {@literal null}.
	 * @param branch must not be {@literal null}.
	 */
	public void checkout(SupportedProject project, Branch branch) {
		checkout(project, branch, BranchCheckoutMode.CREATE_AND_UPDATE);
	}

	/**
	 * Checks out the given {@link Branch} of the given {@link Project}. If the given branch doesn't exist yet, a tracking
	 * branch is created assuming the branch exists in the {@code origin} remote. If the {@link BranchCheckoutMode} is set
	 * to {@code CREATE_AND_UPDATE} the latest changes from the checked out branch will be pulled to make sure we see
	 * them.
	 *
	 * @param project must not be {@literal null}.
	 * @param branch must not be {@literal null}.
	 * @param mode must not be {@literal null}.
	 */
	private void checkout(SupportedProject project, Branch branch, BranchCheckoutMode mode) {

		Assert.notNull(project, "Project must not be null!");
		Assert.notNull(branch, "Branch must not be null!");

		logger.log(project, "Checking out project…");

		doWithGit(project, git -> {

			Optional<Ref> ref = Optional.ofNullable(git.getRepository().findRef(branch.toString()));
			CheckoutCommand checkout = git.checkout().setName(branch.toString()).setForced(true);

			if (ref.isPresent()) {

				logger.log(project, "git checkout %s", branch);

			} else {

				logger.log(project, "git checkout --track -b %s origin/%s", branch, branch);

				checkout.setCreateBranch(true)//
						.setUpstreamMode(SetupUpstreamMode.TRACK)//
						.setStartPoint("origin/".concat(branch.toString()));
			}

			try {
				checkout.call();
			} catch (RefNotFoundException o_O) {
				// TODO:
			}

			switch (mode) {

				case CREATE_ONLY:
					break;
				case CREATE_AND_UPDATE:
				default:
					// Pull latest changes to make sure the branch is up to date
					logger.log(project, "git pull origin %s", branch);

					call(git.pull() //
							.setRemote("origin") //
							.setRebase(true) //
							.setRemoteBranchName(branch.toString()));

					break;
			}
		});

		logger.log(project, "Checkout done!");
	}

	public BranchMapping createMaintenanceBranches(TrainIteration from, TrainIteration to) {

		if (!from.getIteration().isGAIteration()) {
			return BranchMapping.NONE;
		}

		checkout(from);

		return createBranch(from, to);
	}

	public BranchMapping createBranch(TrainIteration from, TrainIteration to) {

		BranchMapping branches = new BranchMapping();

		ExecutionUtils.run(executor, to, module -> {

			ModuleIteration fromIteration = from.getModule(module.getProject());
			Branch current = from.getIteration().isGAIteration() ? Branch.MAIN : Branch.from(fromIteration.getVersion());
			Branch newBranch = createBranch(module);
			checkout(module.getSupportedProject(), newBranch, BranchCheckoutMode.CREATE_ONLY);

			synchronized (branches) {
				branches.add(module.getProject(), current, newBranch);
			}
		});

		return branches;
	}

	private Branch getCurrentBranch(ModuleIteration module) {

		return doWithGit(module.getSupportedProject(), git -> {
			return Branch.from(git.getRepository().getBranch());
		});
	}

	public void removeTags(TrainIteration iteration) {

		ExecutionUtils.run(executor, iteration, module -> {

			SupportedProject project = module.getSupportedProject();
			ArtifactVersion artifactVersion = ArtifactVersion.of(module);

			Optional<Tag> tag = findTagFor(project, artifactVersion);

			if (tag.isEmpty()) {
				logger.log(module, "No tag %s found project %s, skipping.", artifactVersion, project);
				return;
			}

			doWithGit(project, git -> {

				logger.log(module, "git tag -D %s", tag.get());
				git.tagDelete().setTags(tag.get().toString()).call();
			});
		});
	}

	/**
	 * Verify general Git operations.
	 */
	@SneakyThrows
	public void verify(Train train) {

		SupportedProject project = train.getSupportedProject(Projects.BUILD);

		File projectDirectory = workspace.getProjectDirectory(project);

		if (projectDirectory.exists()) {
			FileUtils.deleteDirectory(projectDirectory);
		}

		update(project);
		checkout(project, Branch.MAIN);

		commitRandomFile(project, projectDirectory);

		reset(project, Branch.MAIN);
	}

	private void commitRandomFile(SupportedProject project, File projectDirectory) throws IOException {

		String randomFileName = UUID.randomUUID() + ".txt";
		File randomFile = new File(projectDirectory, randomFileName);

		try (FileOutputStream fos = new FileOutputStream(randomFile)) {
			fos.write(randomFileName.getBytes(StandardCharsets.UTF_8));
		}

		commit(project, new Ticket("1234", "Verify", new TicketStatus() {
			@Override
			public String getLabel() {
				return null;
			}

			@Override
			public boolean isResolved() {
				return false;
			}
		}), "Verify Commit Signing", Optional.empty(), true);
	}

	/**
	 * Creates a version branch for the given {@link ModuleIteration}.
	 *
	 * @param module must not be {@literal null}.
	 * @return
	 */
	private Branch createBranch(ModuleIteration module) {

		Assert.notNull(module, "Module iteration must not be null!");

		Branch branch = Branch.from(module.getVersion());

		doWithGit(module.getSupportedProject(), git -> {

			List<String> existingBranches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL) //
					.call() //
					.stream() //
					.filter(ref -> ref.getName().startsWith("refs/heads/")) //
					.map(it -> it.getName().substring(11)) //
					.collect(Collectors.toList());

			if (existingBranches.contains(branch.toString())) {
				logger.log(module, "Branch %s already exists, skipping creation.", branch);
				return;
			}

			logger.log(module, "git checkout -b %s", branch);
			git.branchCreate().setName(branch.toString()).call();
		});

		return branch;
	}

	/**
	 * Returns the {@link ObjectId} of the commit that is considered the release commit. It is identified by the summary
	 * starting with the release ticket identifier, followed by a dash separated by spaces and the key word
	 * {@code Release}. To prevent skimming through the entire Git history, we expect such a commit to be found within the
	 * 50 most recent commits.
	 *
	 * @param module
	 * @return
	 * @throws Exception
	 */
	private ObjectId getReleaseHash(ModuleIteration module) {
		return findRequiredCommit(module, "Release");
	}

	private ObjectId findRequiredCommit(ModuleIteration module, String summary) {

		Predicate<RevCommit> trigger = calculateFilter(module, summary);

		return findCommit(module, summary).orElseThrow(() -> new IllegalStateException(
				String.format("Did not find a commit with summary starting with '%s' for project %s",
						module.getSupportedProject(), trigger)));
	}

	private Optional<ObjectId> findCommit(ModuleIteration module, String summary) {
		return findCommit(module.getSupportedProject(), calculateFilter(module, summary));
	}

	private Optional<ObjectId> findCommit(SupportedProject project, Predicate<RevCommit> filter) {

		return doWithGit(project, git -> {

			for (RevCommit commit : git.log().setMaxCount(50).call()) {

				if (filter.test(commit)) {
					return Optional.of(commit.getId());
				}
			}

			return Optional.empty();
		});
	}

	private Predicate<RevCommit> calculateFilter(ModuleIteration module, String summary) {

		SupportedProject project = module.getSupportedProject();
		Ticket releaseTicket = issueTracker
				.getRequiredPluginFor(project, () -> String.format("No issue tracker found for project %s!", project))//
				.getReleaseTicketFor(module);

		return revCommit -> {

			if (revCommit.getShortMessage().contains(summary) && revCommit.getFullMessage().contains(releaseTicket.getId())) {
				return true;
			}

			return false;
		};
	}

	/**
	 * Returns the {@link Tag} that represents the {@link ArtifactVersion} of the given {@link Project}.
	 *
	 * @param project
	 * @param version
	 * @return
	 * @throws IOException
	 */
	private Optional<Tag> findTagFor(SupportedProject project, ArtifactVersion version) {

		return getTags(project).stream()//
				.filter(tag -> tag.getArtifactVersion().map(it -> it.equals(version)).orElse(false))//
				.findFirst();
	}

	private Repository getRepository(SupportedProject project) throws IOException {

		File file = workspace.getFile(".git", project);
		if (!file.exists()) {
			throw new FileNotFoundException(
					String.format("No repository found for project '%s' at '%s'", project.getName(), file));
		}

		Repository repository = FileRepositoryBuilder.create(file);

		// ensure symlink usage to avoid plain text checkouts that would break Git commits from the tooling.
		repository.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_SYMLINKS,
				true);

		return repository;
	}

	private void clone(GitProject gitProject) throws Exception {

		SupportedProject project = gitProject.getProject();

		logger.log(project, "No repository found! Cloning from %s…", gitProject.getProjectUri());

		File projectDirectory = workspace.getProjectDirectory(project);
		if (!projectDirectory.exists()) {
			projectDirectory.mkdirs();
		}
		call(Git.cloneRepository() //
				.setURI(gitProject.getProjectUri()) //
				.setDirectory(projectDirectory));

		logger.log(project, "Cloning done!", project);
	}

	private boolean branchExists(SupportedProject project, Branch branch) {

		try (Git git = new Git(getRepository(project))) {

			return git.getRepository().findRef(branch.toString()) != null;

		} catch (Exception o_O) {
			throw new RuntimeException(o_O);
		}
	}

	private void reset(SupportedProject project, Branch branch) {

		logger.log(project, "git reset --hard origin/%s", branch);

		doWithGit(project, git -> {

			git.reset()//
					.setMode(ResetType.HARD)//
					.setRef("origin/".concat(branch.toString()))//
					.call();
		});
	}

	private static String expandSummary(String summary, ModuleIteration module, TrainIteration iteration) {
		return summary.contains("%s") ? String.format(summary, module.getMediumVersionString()) : summary;
	}

	private <T> T doWithGit(SupportedProject project, GitCallback<T> callback) {

		try (Git git = new Git(getRepository(project))) {
			return callback.doWithGit(git);
		} catch (Exception o_O) {
			throw new RuntimeException(o_O);
		}
	}

	private <T> T doWithGit(Repository repository, GitCallback<T> callback) {

		try (Git git = new Git(repository)) {
			return callback.doWithGit(git);
		} catch (Exception o_O) {
			throw new RuntimeException(o_O);
		}
	}

	private void doWithGit(SupportedProject project, VoidGitCallback callback) {

		doWithGit(project, (GitCallback<Void>) git -> {
			callback.doWithGit(git);
			return null;
		});
	}

	private interface GitCallback<T> {
		T doWithGit(Git git) throws Exception;
	}

	private interface VoidGitCallback {
		void doWithGit(Git git) throws Exception;
	}

	private Gpg getGpg() {

		if (gitProperties.hasGpgConfiguration()) {
			return gitProperties.getGpg();
		}

		return gpg;
	}

	private <T, C extends GitCommand<T>> T call(TransportCommand<C, T> command) throws GitAPIException {

		return command.setCredentialsProvider(gitProperties.getCredentials()).call();
	}

	/**
	 * {@link CredentialsProvider} for GPG Keys used with JGit Commit Signing.
	 */
	private static class GpgPassphraseProvider extends CredentialsProvider {

		private final Gpg gpg;

		private GpgPassphraseProvider(Gpg gpg) {
			this.gpg = gpg;
		}

		@Override
		public boolean isInteractive() {
			return false;
		}

		@Override
		public boolean supports(CredentialItem... items) {

			boolean matchesKey = matchesKey(items);
			boolean hasSettableCharArray = Arrays.stream(items).anyMatch(CharArrayType.class::isInstance);

			return matchesKey && hasSettableCharArray;
		}

		private boolean matchesKey(CredentialItem[] items) {

			return Arrays.stream(items).filter(InformationalMessage.class::isInstance) //
					.map(CredentialItem::getPromptText) //
					.map(it -> it.toLowerCase(Locale.US)) //
					.anyMatch(it -> it.contains(gpg.getKeyname().toLowerCase(Locale.US)));
		}

		@Override
		public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {

			if (!matchesKey(items)) {
				return false;
			}

			for (CredentialItem item : items) {
				if (item instanceof CharArrayType type) {
					type.setValueNoCopy(gpg.getPassphrase().toString().toCharArray());

					return true;
				}
			}
			return false;
		}
	}
}
