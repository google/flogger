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
import static com.google.common.flogger.RateLimitStatus.DISALLOW;

import com.google.common.flogger.backend.Metadata;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Rate limiter to support {@code every(N)} functionality.
 *
 * <p>Instances of this class are created for each unique {@link LogSiteKey} for which rate limiting
 * via the {@code LOG_EVERY_N} metadata key is required. This class implements {@code
 * RateLimitStatus} as a mechanism for resetting the rate limiter state.
 *
 * <p>Instances of this class are thread safe.
 */
final class CountingRateLimiter extends RateLimitStatus {
  private static final LogSiteMap<CountingRateLimiter> map =
      new LogSiteMap<CountingRateLimiter>() {
        @Override
        protected CountingRateLimiter initialValue() {
          return new CountingRateLimiter();
        }
      };

  /**
   * Returns the status of the rate limiter, or {@code null} if the {@code LOG_EVERY_N} metadata was
   * not present.
   *
   * <p>The rate limiter status is {@code DISALLOW} until the log count exceeds the specified limit,
   * and then the limiter switches to its pending state and returns an allow status until it is
   * reset.
   */
  @NullableDecl
  static RateLimitStatus check(Metadata metadata, LogSiteKey logSiteKey) {
    Integer rateLimitCount = metadata.findValue(LOG_EVERY_N);
    if (rateLimitCount == null) {
      // Without rate limiter specific metadata, this limiter has no effect.
      return null;
    }
    return map.get(logSiteKey, metadata).incrementAndCheckLogCount(rateLimitCount);
  }

  // By setting the initial value as Integer#MAX_VALUE we ensure that the first time rate limiting
  // is checked, the rate limit count (which is only an Integer) must be reached, placing the
  // limiter into its pending state immediately. If this is the only limiter used, this corresponds
  // to the first log statement always being emitted.
  private final AtomicLong invocationCount = new AtomicLong(Integer.MAX_VALUE);

  // Visible for testing.
  CountingRateLimiter() {}

  /**
   * Increments the invocation count and returns true if it reached the specified rate limit count.
   * This is invoked during post-processing if a rate limiting count was set via {@link
   * LoggingApi#every(int)}.
   */
  // Visible for testing.
  RateLimitStatus incrementAndCheckLogCount(int rateLimitCount) {
    return invocationCount.incrementAndGet() >= rateLimitCount ? this : DISALLOW;
  }

  // Reset function called to move the limiter out of the "pending" state after a log occurs.
  @Override
  public void reset() {
    invocationCount.set(0);
  }
}
