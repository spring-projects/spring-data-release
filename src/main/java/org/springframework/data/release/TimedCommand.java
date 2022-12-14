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

import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.ExecutionProcessor;
import org.springframework.shell.event.ParseResult;
import org.springframework.util.StopWatch;

/**
 * Base class for command implementations who want to get their execution time logged.
 *
 * @author Oliver Gierke
 */
public abstract class TimedCommand implements ExecutionProcessor, CommandMarker {

	private StopWatch watch;

	/*
	 * (non-Javadoc)
	 * @see org.springframework.shell.core.ExecutionProcessor#beforeInvocation(org.springframework.shell.event.ParseResult)
	 */
	@Override
	public ParseResult beforeInvocation(ParseResult invocationContext) {

		watch = new StopWatch();
		watch.start();

		return invocationContext;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.shell.core.ExecutionProcessor#afterReturningInvocation(org.springframework.shell.event.ParseResult, java.lang.Object)
	 */
	@Override
	public void afterReturningInvocation(ParseResult invocationContext, Object result) {
		stopAndLog();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.shell.core.ExecutionProcessor#afterThrowingInvocation(org.springframework.shell.event.ParseResult, java.lang.Throwable)
	 */
	@Override
	public void afterThrowingInvocation(ParseResult invocationContext, Throwable thrown) {
		stopAndLog();
	}

	private void stopAndLog() {

		watch.stop();
		System.out.println(String.format("Took: %.2f sec.", watch.getTotalTimeSeconds()));
	}

	/**
	 * Means to retry commands more than once to ensure they are completed. If any exception is thrown at any time, it
	 * will fall out and try again.
	 *
	 * @param action command(s) that need to be run
	 * @param maxTimes number of times to attempt
	 */
	protected void retry(Runnable action, int maxTimes) {

		int maxAttempts = maxTimes;

		Exception lastException = null;

		while (maxAttempts > 0) {
			try {
				action.run();
				return;
			} catch (Exception e) {
				lastException = e;
				maxAttempts--;
			}
		}

        if (lastException != null) {
            throw new RuntimeException(lastException);
        } else {
            throw new RuntimeException("Unable to complete the action within " + maxTimes + " attempts!");
        }
	}

}
