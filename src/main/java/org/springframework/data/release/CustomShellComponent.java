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
package org.springframework.data.release;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.springframework.shell.core.ExecutionProcessor;
import org.springframework.shell.core.ExecutionStrategy;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.shell.core.SimpleExecutionStrategy;
import org.springframework.shell.event.ParseResult;
import org.springframework.shell.support.logging.HandlerUtils;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Extension of {@link JLineShellComponent} to customize the {@link ExecutionStrategy} to one that can deal with package
 * protected command classes.
 *
 * @author Oliver Gierke
 * @see https://github.com/spring-projects/spring-shell/pull/93
 */
class CustomShellComponent extends JLineShellComponent {

	private final ExecutionStrategy executionStrategy = new CustomExecutionStrategy();

	/*
	 * (non-Javadoc)
	 * @see org.springframework.shell.core.JLineShellComponent#getExecutionStrategy()
	 */
	@Override
	protected ExecutionStrategy getExecutionStrategy() {
		return executionStrategy;
	}

	/**
	 * Effectively a copy of {@link SimpleExecutionStrategy} but with the tweaks provided in PR 93 for Spring shell to
	 * enable execution of package protected command classes.
	 *
	 * @author Oliver Gierke
	 * @see https://github.com/spring-projects/spring-shell/pull/93
	 */
	static class CustomExecutionStrategy implements ExecutionStrategy {

		private static final Logger logger = HandlerUtils.getLogger(SimpleExecutionStrategy.class);

		private final Class<?> mutex = SimpleExecutionStrategy.class;

		public Object execute(ParseResult parseResult) throws RuntimeException {
			Assert.notNull(parseResult, "Parse result required");
			synchronized (mutex) {
				Assert.isTrue(isReadyForCommands(), "SimpleExecutionStrategy not yet ready for commands");
				Object target = parseResult.getInstance();
				if (target instanceof ExecutionProcessor executionProcessor) {
					ExecutionProcessor processor = executionProcessor;
					parseResult = processor.beforeInvocation(parseResult);
					try {
						Object result = invoke(parseResult);
						processor.afterReturningInvocation(parseResult, result);
						return result;
					} catch (Throwable th) {
						processor.afterThrowingInvocation(parseResult, th);
						return handleThrowable(th);
					}
				} else {
					return invoke(parseResult);
				}
			}
		}

		private Object invoke(ParseResult parseResult) {
			try {
				Method method = parseResult.getMethod();
				ReflectionUtils.makeAccessible(method);
				return ReflectionUtils.invokeMethod(method, parseResult.getInstance(), parseResult.getArguments());
			} catch (Throwable th) {
				logger.severe("Command failed");
				return handleThrowable(th);
			}
		}

		private Object handleThrowable(Throwable th) {
			if (th instanceof Error error) {
				throw error;
			}
			if (th instanceof RuntimeException exception) {
				throw exception;
			}
			throw new RuntimeException(th);
		}

		public boolean isReadyForCommands() {
			return true;
		}

		public void terminate() {}
	}
}
