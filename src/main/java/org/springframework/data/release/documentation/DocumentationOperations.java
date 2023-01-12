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

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.data.release.utils.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * @author Christoph Strobl
 */
@Component
@RequiredArgsConstructor
class DocumentationOperations {

	private final @NonNull Logger logger;

	PageStats checkDocumentation(String url) {
		PageStats stats = new PageStats();
		stats.links(new LinkChecker(logger).inspect(url));
		return stats;
	}

	enum ReportFlags {

		ERROR(1), REDIRECT(2), OK(4), ALL(8);

		int bin;

		ReportFlags(int bin) {
			this.bin = bin;
		}

		static int flagsOf(ReportFlags... flags) {
			int value = 0;
			for (ReportFlags opt : flags) {
				value = value | opt.bin;
			}
			return value;
		}
	}

	static class PageStats {

		public static final int ERROR = 1;  // Binary 00001
		public static final int REDIRECT = 2;  // Binary 00010
		public static final int OK = 4;  // Binary 00100
		public static final int ALL = 8;  // Binary 01000

		LinkStats linkStats;

		String prettyPrint(ReportFlags... options) {

			if (linkStats != null) {
				return linkStats.prettyPrint(options);
			}
			return "PageStats n/a";
		}

		void links(LinkStats linkStats) {
			this.linkStats = linkStats;
		}
	}


	static class LinkStats {

		Map<String, HttpStatus> resultMap = new LinkedHashMap<>(200);

		public HttpStatus computeIfAbsent(String key, Function<? super String, ? extends HttpStatus> mappingFunction) {
			return resultMap.computeIfAbsent(key, mappingFunction);
		}

		int size() {
			return resultMap.size();
		}

		String prettyPrint() {
			return prettyPrint(ReportFlags.ALL);
		}

		String prettyPrint(ReportFlags... options) {

			int flags = ObjectUtils.isEmpty(options) ? ReportFlags.flagsOf(ReportFlags.ALL) : ReportFlags.flagsOf(options);

			return resultMap.entrySet().stream().filter(entry -> {
				if ((flags & ReportFlags.ALL.bin) == ReportFlags.ALL.bin) {
					return true;
				}
				if (entry.getValue().is2xxSuccessful() && (flags & ReportFlags.OK.bin) == ReportFlags.OK.bin) {
					return true;
				}
				if (entry.getValue().is3xxRedirection() && (flags & ReportFlags.REDIRECT.bin) == ReportFlags.REDIRECT.bin) {
					return true;
				}
				if (entry.getValue().is4xxClientError() && (flags & ReportFlags.ERROR.bin) == ReportFlags.ERROR.bin) {
					return true;
				}
				return false;
			}).sorted(Comparator.comparingInt(o -> o.getValue().value())).map(entry -> {
				Ansi ansi = Ansi.ansi();
				if (entry.getValue().is2xxSuccessful()) {
					ansi.fg(Color.GREEN);
				} else if (entry.getValue().is4xxClientError())
					ansi.fg(Color.RED);
				else if (entry.getValue().is3xxRedirection()) {
					ansi.fg(Color.YELLOW);
				}
				return ansi.a(entry.getValue()).fg(Color.DEFAULT).a(": " + entry.getKey()).toString();
			}).collect(Collectors.joining("\r\n"));
		}
	}


	@RequiredArgsConstructor
	static class LinkChecker {

		private final Logger logger;

		LinkStats inspect(String url) throws RuntimeException {

			logger.log("", "Collecting links from: %s", url);

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

			logger.log("", "Found %s links.", links.size());

			LinkStats stats = new LinkStats();

			links.forEach(link -> {
				if (link.attr("href").startsWith("#")) {
					return;
				}
				checkUrl(link.attr("href"), stats);
			});

			logger.log("", "Analyzed %s external links.", stats.size());

			return stats;
		}

		private void checkUrl(String url, LinkStats stats) {

			stats.computeIfAbsent(url, key -> {
				try {
					HttpURLConnection connection = (HttpURLConnection) new URL(key).openConnection();
					connection.setRequestMethod("HEAD");
					connection.setConnectTimeout(5000);
					connection.setReadTimeout(8000);
					return HttpStatus.valueOf(connection.getResponseCode());
				} catch (Exception ex) {
					return HttpStatus.valueOf(500);
				}
			});
		}
	}
}
