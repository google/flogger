/*
 * Copyright (C) 2023 The Flogger Authors.
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

import static com.google.common.flogger.LogContext.Key.LOG_SAMPLE_EVERY_N;
import static com.google.common.flogger.RateLimitStatus.DISALLOW;

import com.google.common.flogger.backend.Metadata;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Rate limiter to support {@code onAverageEvery(N)} functionality.
 *
 * <p>Instances of this class are created for each unique {@link LogSiteKey} for which rate limiting
 * via the {@code LOG_SAMPLE_EVERY_N} metadata key is required. This class implements {@code
 * RateLimitStatus} as a mechanism for resetting its own state.
 *
 * <p>This class is thread safe.
 */
final class SamplingRateLimiter extends RateLimitStatus {
  private static final LogSiteMap<SamplingRateLimiter> map =
      new LogSiteMap<SamplingRateLimiter>() {
        @Override
        protected SamplingRateLimiter initialValue() {
          return new SamplingRateLimiter();
        }
      };

  @NullableDecl
  static RateLimitStatus check(Metadata metadata, LogSiteKey logSiteKey) {
    Integer rateLimitCount = metadata.findValue(LOG_SAMPLE_EVERY_N);
    if (rateLimitCount == null || rateLimitCount <= 0) {
      // Without valid rate limiter specific metadata, this limiter has no effect.
      return null;
    }
    return map.get(logSiteKey, metadata).sampleOneIn(rateLimitCount);
  }

  // Even though Random is synchonized, we have to put it in a ThreadLocal to avoid thread
  // contention. We cannot use ThreadLocalRandom (yet) due to JDK level.
  private static final ThreadLocal<Random> random = new ThreadLocal<Random>() {
    @Override
    protected Random initialValue() {
      return new Random();
    }
  };

  // Visible for testing.
  final AtomicInteger pendingCount = new AtomicInteger();

  // Visible for testing.
  SamplingRateLimiter() {}

  RateLimitStatus sampleOneIn(int rateLimitCount) {
    // Always "roll the dice" and adjust the count if necessary (even if we were already
    // pending). This means that in the long run we will account for every time we roll a
    // zero and the number of logs will end up statistically close to 1-in-N (even if at
    // times they can be "bursty" due to the action of other rate limiting mechanisms).
    int pending;
    if (random.get().nextInt(rateLimitCount) == 0) {
      pending = pendingCount.incrementAndGet();
    } else {
      pending = pendingCount.get();
    }
    return pending > 0 ? this : DISALLOW;
  }

  @Override
  public void reset() {
    pendingCount.decrementAndGet();
  }
}
