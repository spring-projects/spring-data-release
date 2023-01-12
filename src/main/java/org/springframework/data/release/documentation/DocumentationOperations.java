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

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * @author Christoph Strobl
 */
@Component
class DocumentationOperations {

	PageStats checkDocumentation(String url) {
		PageStats stats = new PageStats();
		stats.links(new LinkChecker().inspect(url));
		return stats;
	}

	static class PageStats {

		LinkStats linkStats;

		void prettyPrint() {
			if (linkStats != null) {
				linkStats.prettyPrint();
			}
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

		void prettyPrint() {

			resultMap.entrySet().stream().sorted(Comparator.comparingInt(o -> o.getValue().value())).forEach(entry -> {
				Ansi ansi = Ansi.ansi();
				if (entry.getValue().is2xxSuccessful()) {
					ansi.fg(Color.GREEN);
				} else if (entry.getValue().is4xxClientError())
					ansi.fg(Color.RED);
				else if (entry.getValue().is3xxRedirection()) {
					ansi.fg(Color.YELLOW);
				}
				System.out.println(ansi.a(entry.getValue()).fg(Color.DEFAULT).a(": " + entry.getKey()).toString());
			});
		}
	}


	static class LinkChecker {

		LinkStats inspect(String url) throws RuntimeException {

			System.out.println("Collecting links from: " + url);

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

			System.out.println("Found " + links.size() + " links.");
			System.out.print("Processing ");

			LinkStats stats = new LinkStats();

			links.forEach(link -> {
				if (link.attr("href").startsWith("#")) {
					return;
				}
				checkUrl(link.attr("href"), stats);
			});
			System.out.println();

			return stats;
		}

		private void checkUrl(String url, LinkStats stats) {

			stats.computeIfAbsent(url, key -> {
				System.out.print(".");
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
