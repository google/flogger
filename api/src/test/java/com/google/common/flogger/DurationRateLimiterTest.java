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

import static com.google.common.flogger.LogContext.Key.LOG_AT_MOST_EVERY;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.flogger.DurationRateLimiter.RateLimitPeriod;
import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DurationRateLimiterTest {
  @Test
  public void testMetadataKey() {
    FakeMetadata metadata =
        new FakeMetadata()
            .add(LOG_AT_MOST_EVERY, DurationRateLimiter.newRateLimitPeriod(1, SECONDS));
    LogSite logSite = FakeLogSite.unique();

    // The first log is always emitted (and sets the deadline).
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, logSite, 1_000_000_000L))
        .isTrue();
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, logSite, 1_500_000_000L))
        .isFalse();
    // Not supplying the metadata disables rate limiting.
    assertThat(
            DurationRateLimiter.shouldLogForTimestamp(new FakeMetadata(), logSite, 1_500_000_000L))
        .isTrue();
    // The next log is emitted after 1 second.
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, logSite, 1_999_999_999L))
        .isFalse();
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, logSite, 2_000_000_000L))
        .isTrue();
  }

  @Test
  public void testDistinctLogSites() {
    FakeMetadata metadata =
        new FakeMetadata()
            .add(LOG_AT_MOST_EVERY, DurationRateLimiter.newRateLimitPeriod(1, SECONDS));
    LogSite fooLog = FakeLogSite.unique();
    LogSite barLog = FakeLogSite.unique();

    // The first log is always emitted (and sets the deadline).
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, fooLog, 1_000_000_000L))
        .isTrue();
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, barLog, 5_000_000_000L))
        .isTrue();
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, fooLog, 1_500_000_000L))
        .isFalse();
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, barLog, 5_500_000_000L))
        .isFalse();
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, fooLog, 2_000_000_000L))
        .isTrue();
    assertThat(DurationRateLimiter.shouldLogForTimestamp(metadata, barLog, 6_000_000_000L))
        .isTrue();
  }

  @Test
  public void testCheckLastTimestamp() {

    DurationRateLimiter limiter = new DurationRateLimiter();
    RateLimitPeriod period = DurationRateLimiter.newRateLimitPeriod(1, SECONDS);
    // Arbitrary start time (but within the first period to ensure we still log the first call).
    long startNanos = 123456000L;

    // Always log for the first call, but not again in the same period.
    assertThat(limiter.checkLastTimestamp(startNanos, period)).isTrue();
    assertThat(period.toString()).isEqualTo("1 SECONDS");
    assertThat(limiter.checkLastTimestamp(startNanos + MILLISECONDS.toNanos(500), period))
        .isFalse();

    // Return true exactly when next period begins.
    long nextStartNanos = startNanos + SECONDS.toNanos(1);
    assertThat(limiter.checkLastTimestamp(nextStartNanos - 1, period)).isFalse();
    assertThat(limiter.checkLastTimestamp(nextStartNanos, period)).isTrue();
    assertThat(period.toString()).isEqualTo("1 SECONDS [skipped: 2]");

    // Only return true once, even for duplicate calls.
    assertThat(limiter.checkLastTimestamp(nextStartNanos, period)).isFalse();
  }

  @Test
  public void testPeriodToString() {
    assertThat(DurationRateLimiter.newRateLimitPeriod(23, SECONDS).toString())
        .isEqualTo("23 SECONDS");
  }
}
