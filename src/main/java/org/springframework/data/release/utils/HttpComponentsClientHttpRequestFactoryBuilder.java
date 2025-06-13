/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.data.release.utils;

import java.net.URI;

import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Builder for {@link HttpComponentsClientHttpRequestFactory}.
 *
 * @author Mark Paluch
 */
public class HttpComponentsClientHttpRequestFactoryBuilder {

	private final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
	private final BasicAuthCache authCache = new BasicAuthCache();

	private HttpComponentsClientHttpRequestFactoryBuilder() {

	}

	public static HttpComponentsClientHttpRequestFactoryBuilder builder() {
		return new HttpComponentsClientHttpRequestFactoryBuilder();
	}

	/**
	 * Add pre-emptive authentication to the {@link HttpComponentsClientHttpRequestFactory}.
	 *
	 * @param uri
	 * @param credentials
	 * @return
	 */
	public HttpComponentsClientHttpRequestFactoryBuilder withAuthentication(String uri,
			HttpBasicCredentials credentials) {

		addPreemptiveAuth(credsProvider, authCache, uri, credentials);
		return this;
	}

	/**
	 * Build the {@link HttpComponentsClientHttpRequestFactory}.
	 *
	 * @return
	 */
	public HttpComponentsClientHttpRequestFactory build() {

		CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(credsProvider).build();

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

		factory.setHttpContextFactory((httpMethod, uri) -> {

			ContextBuilder contextBuilder = ContextBuilder.create();
			contextBuilder.useAuthCache(authCache);
			return contextBuilder.build();
		});

		return factory;
	}

	private static void addPreemptiveAuth(BasicCredentialsProvider credsProvider, AuthCache authCache, String requestUrl,
			HttpBasicCredentials credentials) {

		HttpHost host = HttpHost.create(URI.create(requestUrl));

		UsernamePasswordCredentials userPassword = new UsernamePasswordCredentials(credentials.getUsername(),
				credentials.getPassword().toCharArray());
		credsProvider.setCredentials(new AuthScope(host), userPassword);

		BasicScheme basicScheme = new BasicScheme();
		basicScheme.initPreemptive(userPassword);

		authCache.put(host, basicScheme);
	}

}
