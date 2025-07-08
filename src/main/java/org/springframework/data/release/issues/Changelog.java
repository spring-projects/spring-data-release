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
package org.springframework.data.release.issues;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.io.IOUtils;

import org.springframework.data.release.model.ArtifactVersion;
import org.springframework.data.release.model.ModuleIteration;
import org.springframework.format.datetime.DateFormatter;

/**
 * @author Oliver Gierke
 */
@RequiredArgsConstructor(staticName = "of")
@EqualsAndHashCode
public class Changelog {

	@Getter private final ModuleIteration module;
	private final Tickets tickets;

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return toString(true, "");

	}

	public String toString(boolean header, String indentation) {
		ArtifactVersion version = ArtifactVersion.of(module);

		String headline = "Changes in version %s (%s)".formatted(version,
				new DateFormatter("YYYY-MM-dd").print(new Date(), Locale.US));

		StringBuilder builder = new StringBuilder();

		if (header) {

			builder.append(indentation).append(headline).append(IOUtils.LINE_SEPARATOR).append(indentation);

			for (int i = 0; i < headline.length(); i++) {
				builder.append("-");
			}

			builder.append(IOUtils.LINE_SEPARATOR);
		}

		for (Ticket ticket : tickets) {

			String summary = ticket.getSummary();

			builder.append(indentation).append("* ").append(ticket.getId()).append(" - ")
					.append(summary != null ? summary.trim() : "");

			if (!summary.endsWith(".")) {
				builder.append(".");
			}

			builder.append(IOUtils.LINE_SEPARATOR);
		}

		return builder.toString();
	}
}
