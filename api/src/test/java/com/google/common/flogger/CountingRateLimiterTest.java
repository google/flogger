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
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CountingRateLimiterTest {
  @Test
  public void testMetadataKey() {
    FakeMetadata metadata = new FakeMetadata().add(LOG_EVERY_N, 3);
    LogSite logSite = FakeLogSite.unique();

    // The first log is always emitted.
    assertThat(CountingRateLimiter.shouldLog(metadata, logSite)).isTrue();
    assertThat(CountingRateLimiter.shouldLog(metadata, logSite)).isFalse();
    // Not supplying the metadata disables rate limiting.
    assertThat(CountingRateLimiter.shouldLog(new FakeMetadata(), logSite)).isTrue();
    assertThat(CountingRateLimiter.shouldLog(metadata, logSite)).isFalse();
    // The fourth log is emitted (ignoring the case when there was no metadata).
    assertThat(CountingRateLimiter.shouldLog(metadata, logSite)).isTrue();
  }

  @Test
  public void testDistinctLogSites() {
    FakeMetadata metadata = new FakeMetadata().add(LOG_EVERY_N, 3);
    LogSite fooLog = FakeLogSite.unique();
    LogSite barLog = FakeLogSite.unique();

    // The first log is always emitted.
    assertThat(CountingRateLimiter.shouldLog(metadata, fooLog)).isTrue();
    assertThat(CountingRateLimiter.shouldLog(metadata, barLog)).isTrue();

    assertThat(CountingRateLimiter.shouldLog(metadata, fooLog)).isFalse();
    assertThat(CountingRateLimiter.shouldLog(metadata, fooLog)).isFalse();

    assertThat(CountingRateLimiter.shouldLog(metadata, barLog)).isFalse();
    assertThat(CountingRateLimiter.shouldLog(metadata, barLog)).isFalse();

    assertThat(CountingRateLimiter.shouldLog(metadata, fooLog)).isTrue();
    assertThat(CountingRateLimiter.shouldLog(metadata, barLog)).isTrue();
  }

  @Test
  public void testIncrementAndCheckInvocationCount() {
    CountingRateLimiter limiter = new CountingRateLimiter();

    // Always log for the first call.
    assertThat(limiter.incrementAndCheckInvocationCount(2)).isTrue();

    // Alternating for a rate limit count of 2
    assertThat(limiter.incrementAndCheckInvocationCount(2)).isFalse();
    assertThat(limiter.incrementAndCheckInvocationCount(2)).isTrue();

    // Every third for a rate limit count of 3 (counter starts at 3, so returns true immediately).
    assertThat(limiter.incrementAndCheckInvocationCount(3)).isTrue();
    assertThat(limiter.incrementAndCheckInvocationCount(3)).isFalse();
    assertThat(limiter.incrementAndCheckInvocationCount(3)).isFalse();
    assertThat(limiter.incrementAndCheckInvocationCount(3)).isTrue();
  }
}
