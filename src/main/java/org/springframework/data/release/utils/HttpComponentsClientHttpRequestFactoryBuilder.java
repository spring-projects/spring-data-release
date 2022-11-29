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

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.data.util.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Builder for {@link HttpComponentsClientHttpRequestFactory}.
 *
 * @author Mark Paluch
 */
public class HttpComponentsClientHttpRequestFactoryBuilder {

	private final CredentialsProvider credsProvider = new BasicCredentialsProvider();
	private final AuthCache authCache = new BasicAuthCache();

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

		Lazy<CloseableHttpClient> lazy = Lazy
				.of(() -> HttpClientBuilder.create().setDefaultCredentialsProvider(credsProvider).build());

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory() {
			@Override
			public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
				setHttpClient(lazy.get());
				return super.createRequest(uri, httpMethod);
			}
		};

		factory.setHttpContextFactory((httpMethod, uri) -> {
			HttpClientContext context = HttpClientContext.create();
			context.setAuthCache(authCache);
			return context;
		});

		return factory;
	}

	private static void addPreemptiveAuth(CredentialsProvider credsProvider, AuthCache authCache, String requestUrl,
			HttpBasicCredentials credentials) {

		HttpHost host = HttpHost.create(requestUrl);

		credsProvider.setCredentials(new AuthScope(host),
				new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword().toString()));

		authCache.put(host, new BasicScheme());
	}

}
