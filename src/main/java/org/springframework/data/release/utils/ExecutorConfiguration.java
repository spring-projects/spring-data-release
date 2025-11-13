/*
 * Copyright 2020-2022 the original author or authors.
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
package org.springframework.data.release.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;

/**
 * @author Mark Paluch
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
class ExecutorConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "maven", name = "parallelize")
	public ThreadPoolExecutorFactoryBean threadPoolExecutorFactoryBean() {

		int processors = Runtime.getRuntime().availableProcessors();
		int threadCount = Math.max(4, processors - 4);
		log.info(String.format("Setting up Executor Service with %d Threads, %d processors", threadCount, processors));

		ThreadPoolExecutorFactoryBean scheduler = new ThreadPoolExecutorFactoryBean();
		scheduler.setCorePoolSize(threadCount);
		scheduler.setQueueCapacity(32);

		return scheduler;
	}

	@Bean
	@ConditionalOnProperty(prefix = "maven", name = "parallelize", matchIfMissing = true, havingValue = "false")
	public ExecutorService executorService() {
		return ExecutionUtils.immediateExecutorService();
	}

}
