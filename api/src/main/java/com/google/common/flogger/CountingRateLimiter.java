/*
 * Copyright (C) 2014 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger;

import static com.google.common.flogger.LogContext.Key.LOG_EVERY_N;

import com.google.common.flogger.backend.Metadata;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter to support {@code every(N)} functionality. This class is mutable, but thread safe.
 */
final class CountingRateLimiter {
  private static final LogSiteMap<CountingRateLimiter> map =
      new LogSiteMap<CountingRateLimiter>() {
        @Override
        protected CountingRateLimiter initialValue() {
          return new CountingRateLimiter();
        }
      };

  static boolean shouldLog(Metadata metadata, LogSiteKey logSiteKey) {
    // Fast path is "there's no metadata so return true" and this must not allocate.
    Integer rateLimitCount = metadata.findValue(LOG_EVERY_N);
    if (rateLimitCount == null) {
      return true;
    }
    return map.get(logSiteKey, metadata).incrementAndCheckInvocationCount(rateLimitCount);
  }

  private final AtomicLong invocationCount = new AtomicLong();

  /**
   * Increments the invocation count and returns true if it was a multiple of the specified rate
   * limit count; implying that the log statement should be emitted. This is invoked during
   * post-processing if a rate limiting count was set via {@link LoggingApi#every(int)}.
   */
  // Visible for testing.
  boolean incrementAndCheckInvocationCount(int rateLimitCount) {
    // Assume overflow cannot happen for a Long counter.
    return (invocationCount.getAndIncrement() % rateLimitCount) == 0;
  }
}
