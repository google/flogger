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
import static com.google.common.flogger.RateLimitStatus.DISALLOW;
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
  private static final long FAKE_TIMESTAMP = SECONDS.toNanos(1234);

  @Test
  public void testCheck_noMetadataReturnsNull() {
    // Not supplying the metadata key ignores rate limiting by returning null.
    assertThat(DurationRateLimiter.check(new FakeMetadata(), FakeLogSite.unique(), FAKE_TIMESTAMP))
        .isNull();
  }

  @Test
  public void testCheck_rateLimitsAsExpected() {
    RateLimitPeriod oncePerSecond = DurationRateLimiter.newRateLimitPeriod(1, SECONDS);
    FakeMetadata metadata = new FakeMetadata().add(LOG_AT_MOST_EVERY, oncePerSecond);
    LogSite logSite = FakeLogSite.unique();

    for (int n = 0; n < 100; n++) {
      // Increment by 1/10 of a second per log. We should then log once per 10 logs.
      long timestamp = FAKE_TIMESTAMP + (n * MILLISECONDS.toNanos(100));
      int skipCount =
          RateLimitStatus.checkStatus(
              DurationRateLimiter.check(metadata, logSite, timestamp), logSite, metadata);
      boolean shouldLog = skipCount != -1;
      assertThat(shouldLog).isEqualTo(n % 10 == 0);
    }
  }

  @Test
  public void testCheck_distinctLogSites() {
    RateLimitPeriod oncePerSecond = DurationRateLimiter.newRateLimitPeriod(1, SECONDS);
    FakeMetadata metadata = new FakeMetadata().add(LOG_AT_MOST_EVERY, oncePerSecond);
    LogSite fooLog = FakeLogSite.unique();
    LogSite barLog = FakeLogSite.unique();

    long timestamp = FAKE_TIMESTAMP;
    RateLimitStatus allowFoo = DurationRateLimiter.check(metadata, fooLog, timestamp);
    RateLimitStatus allowBar = DurationRateLimiter.check(metadata, barLog, timestamp);
    assertThat(allowFoo).isNotEqualTo(allowBar);
    assertThat(DurationRateLimiter.check(metadata, fooLog, timestamp)).isSameInstanceAs(allowFoo);
    assertThat(DurationRateLimiter.check(metadata, barLog, timestamp)).isSameInstanceAs(allowBar);

    // "foo" is reset so it moves into its rate-limiting state, but "bar" stays pending.
    allowFoo.reset();
    timestamp += MILLISECONDS.toNanos(100);
    assertThat(DurationRateLimiter.check(metadata, fooLog, timestamp)).isSameInstanceAs(DISALLOW);
    assertThat(DurationRateLimiter.check(metadata, barLog, timestamp)).isSameInstanceAs(allowBar);

    // We reset "bar" after an additional 100ms has passed. Both limiters are rate-limiting.
    allowBar.reset();
    timestamp += MILLISECONDS.toNanos(100);
    assertThat(DurationRateLimiter.check(metadata, fooLog, timestamp)).isSameInstanceAs(DISALLOW);
    assertThat(DurationRateLimiter.check(metadata, barLog, timestamp)).isSameInstanceAs(DISALLOW);

    // After 800ms, it has been 1 second since "foo" was reset, but only 900ms since "bar" was
    // reset, so "foo" becomes pending and "bar" stays rate-limiting.
    timestamp += MILLISECONDS.toNanos(800);
    assertThat(DurationRateLimiter.check(metadata, fooLog, timestamp)).isSameInstanceAs(allowFoo);
    assertThat(DurationRateLimiter.check(metadata, barLog, timestamp)).isSameInstanceAs(DISALLOW);

    // After another 100ms, both limiters are now pending again.
    timestamp += MILLISECONDS.toNanos(100);
    assertThat(DurationRateLimiter.check(metadata, fooLog, timestamp)).isSameInstanceAs(allowFoo);
    assertThat(DurationRateLimiter.check(metadata, barLog, timestamp)).isSameInstanceAs(allowBar);
  }

  @Test
  public void testCheckLastTimestamp() {
    DurationRateLimiter limiter = new DurationRateLimiter();
    RateLimitPeriod period = DurationRateLimiter.newRateLimitPeriod(1, SECONDS);
    // Arbitrary start time (but within the first period to ensure we still log the first call).
    long timestamp = FAKE_TIMESTAMP;

    // Always log for the first call, but not again in the same period.
    RateLimitStatus allowStatus = limiter.checkLastTimestamp(timestamp, period);
    assertThat(allowStatus).isNotEqualTo(DISALLOW);
    // Within the rate limit period we still return "allow" (because we have not been reset).
    timestamp += MILLISECONDS.toNanos(500);
    assertThat(limiter.checkLastTimestamp(timestamp, period)).isSameInstanceAs(allowStatus);
    // This sets the new log time to the last seen timestamp.
    allowStatus.reset();
    // Within 1 SECONDS, we disallow logging.
    timestamp += MILLISECONDS.toNanos(500);
    assertThat(limiter.checkLastTimestamp(timestamp, period)).isSameInstanceAs(DISALLOW);
    timestamp += MILLISECONDS.toNanos(499);
    assertThat(limiter.checkLastTimestamp(timestamp, period)).isSameInstanceAs(DISALLOW);
    // And at exactly 1 SECOND later, we allow logging again.
    timestamp += MILLISECONDS.toNanos(1);
    assertThat(limiter.checkLastTimestamp(timestamp, period)).isSameInstanceAs(allowStatus);
  }

  @Test
  public void testPeriodToString() {
    assertThat(DurationRateLimiter.newRateLimitPeriod(23, SECONDS).toString())
        .isEqualTo("23 SECONDS");
  }
}
