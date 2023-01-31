/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.data.release.documentation;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.data.release.model.Project;
import org.springframework.data.release.utils.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Christoph Strobl
 */
@Component
@RequiredArgsConstructor
class DocumentationOperations {

	private final @NonNull Logger logger;
	private final WebClient webClient;

	PageStats checkDocumentation(Project project, String url) {
		return new PageStats(project, new LinkChecker(logger).inspect(project, url));
	}

	enum ReportFlag {

		ERROR(1), REDIRECT(2), OK(4), ALL(8), TO_BE_UPDATED(16);

		int bin;

		ReportFlag(int bin) {
			this.bin = bin;
		}

	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	static class ReportFlags {

		private final EnumSet<ReportFlag> flags;

		public static ReportFlags parse(String options) {

			EnumSet<ReportFlag> set = EnumSet.noneOf(ReportFlag.class);
			if (options != null) {
				Arrays.stream(options.split(",")).map(ReportFlag::valueOf).forEach(set::add);
			} else {
				set.add(ReportFlag.TO_BE_UPDATED);
			}

			return new ReportFlags(set);
		}

		public boolean isIncluded(HttpStatus status) {

			if (flags.contains(ReportFlag.ALL)) {
				return true;
			}

			if (flags.contains(ReportFlag.TO_BE_UPDATED)) {
				return !status.is2xxSuccessful();
			}

			if (status.is2xxSuccessful() && flags.contains(ReportFlag.OK)) {
				return true;
			}
			if (status.is3xxRedirection() && flags.contains(ReportFlag.REDIRECT)) {
				return true;
			}
			if (status.is4xxClientError() && flags.contains(ReportFlag.ERROR)) {
				return true;
			}

			return false;
		}
	}

	@RequiredArgsConstructor
	static class PageStats {

		public static final int ERROR = 1; // Binary 00001
		public static final int REDIRECT = 2; // Binary 00010
		public static final int OK = 4; // Binary 00100
		public static final int ALL = 8; // Binary 01000

		@Getter final Project project;
		final LinkStats linkStats;

		public PageStats filter(ReportFlags reportFlags) {
			return new PageStats(project, linkStats.filter(reportFlags));
		}

		public PageStats sort(Comparator<CheckedLink> comparator){
			return new PageStats(project, linkStats.sort(comparator));
		}

		public void forEach(Consumer<CheckedLink> consumer) {
			linkStats.checkedLinks.forEach(consumer);
		}
	}

	@RequiredArgsConstructor
	static class LinkStats {

		final List<CheckedLink> checkedLinks;

		int size() {
			return checkedLinks.size();
		}

		List<CheckedLink> getResults() {
			return checkedLinks;
		}

		public LinkStats filter(ReportFlags reportFlags) {

			List<CheckedLink> filtered = checkedLinks.stream().filter(entry -> {
				HttpStatus status = entry.getResult();
				return reportFlags.isIncluded(status);
			}).collect(Collectors.toList());

			return new LinkStats(filtered);
		}

		public LinkStats sort(Comparator<CheckedLink> comparator) {

			List<CheckedLink> sorted = checkedLinks.stream().sorted(comparator).collect(Collectors.toList());

			return new LinkStats(sorted);
		}
	}

	@Value
	static class CheckedLink {
		String url;
		HttpStatus result;
	}

	@RequiredArgsConstructor
	class LinkChecker {

		private final Logger logger;

		LinkStats inspect(Project project, String url) throws RuntimeException {

			logger.log(project, "Collecting links from: %s", url);

			Document doc = null;
			try {
				if (url.startsWith("http://") || url.startsWith("https://")) {
					doc = Jsoup.parse(new URL(url), 5000);
				} else {
					if (url.startsWith("file://")) {
						url = url.replace("file://", "");
					}
					doc = Jsoup.parse(new File(url), "UTF-8");
				}
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

			Elements links = doc.select("a[href]"); // a with href

			logger.log(project, "Found %s links.", links.size());

			Map<String, CompletableFuture<HttpStatus>> resultMap = new LinkedHashMap<>(200);

			links.forEach(link -> {
				String href = link.attr("href");

				if (href.startsWith("#")) {
					return;
				}

				if (href.contains("#")) {
					href = href.substring(0, href.indexOf('#'));
				}

				checkUrl(resultMap, href);
			});

			List<CheckedLink> checkedLinks = resultMap.entrySet().stream()
					.map(it -> new CheckedLink(it.getKey(), it.getValue().join())).collect(Collectors.toList());

			LinkStats stats = new LinkStats(checkedLinks);

			logger.log(project, "Analyzed %s external links.", stats.size());

			return stats;
		}

		private void checkUrl(Map<String, CompletableFuture<HttpStatus>> resultMap, String url) {

			resultMap.computeIfAbsent(url, key -> webClient.get().uri(url)
					.exchangeToMono(clientResponse -> clientResponse.toBodilessEntity().thenReturn(clientResponse.statusCode()))
					.onErrorReturn(HttpStatus.INTERNAL_SERVER_ERROR).toFuture());
		}
	}
}
