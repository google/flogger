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
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CountingRateLimiterTest {
  @Test
  public void testCheck_noMetadataReturnsNull() {
    // Not supplying the metadata key ignores rate limiting by returning null.
    assertThat(CountingRateLimiter.check(new FakeMetadata(), FakeLogSite.unique())).isNull();
  }

  @Test
  public void testCheck_rateLimitsAsExpected() {
    FakeMetadata metadata = new FakeMetadata().add(LOG_EVERY_N, 3);
    LogSite logSite = FakeLogSite.unique();
    for (int n = 0; n < 100; n++) {
      int skipCount =
          RateLimitStatus.checkStatus(
              CountingRateLimiter.check(metadata, logSite), logSite, metadata);
      boolean shouldLog = skipCount != -1;
      assertThat(shouldLog).isEqualTo(n % 3 == 0);
    }
  }

  @Test
  public void testCheck_distinctLogSites() {
    FakeMetadata metadata = new FakeMetadata().add(LOG_EVERY_N, 3);
    LogSite fooLog = FakeLogSite.unique();
    LogSite barLog = FakeLogSite.unique();

    RateLimitStatus allowFoo = CountingRateLimiter.check(metadata, fooLog);
    RateLimitStatus allowBar = CountingRateLimiter.check(metadata, barLog);
    assertThat(allowFoo).isNotEqualTo(allowBar);
    assertThat(CountingRateLimiter.check(metadata, fooLog)).isSameInstanceAs(allowFoo);
    assertThat(CountingRateLimiter.check(metadata, barLog)).isSameInstanceAs(allowBar);

    // "foo" is reset so it moves into its rate-limiting state, but "bar" stays pending.
    allowFoo.reset();
    assertThat(CountingRateLimiter.check(metadata, fooLog)).isSameInstanceAs(DISALLOW);
    assertThat(CountingRateLimiter.check(metadata, barLog)).isSameInstanceAs(allowBar);

    allowBar.reset();
    assertThat(CountingRateLimiter.check(metadata, fooLog)).isSameInstanceAs(DISALLOW);
    assertThat(CountingRateLimiter.check(metadata, barLog)).isSameInstanceAs(DISALLOW);
  }

  @Test
  public void testIncrementAndCheckLogCount() {
    CountingRateLimiter limiter = new CountingRateLimiter();

    // The rate limiter starts in the pending state and always returns the same allow status.
    RateLimitStatus allowStatus = limiter.incrementAndCheckLogCount(3);
    assertThat(allowStatus).isNotEqualTo(DISALLOW);
    assertThat(limiter.incrementAndCheckLogCount(3)).isSameInstanceAs(allowStatus);
    assertThat(limiter.incrementAndCheckLogCount(3)).isSameInstanceAs(allowStatus);
    assertThat(limiter.incrementAndCheckLogCount(3)).isSameInstanceAs(allowStatus);
    // After a reset, we should disallow 2 logs before re-entering the pending state.
    allowStatus.reset();

    assertThat(limiter.incrementAndCheckLogCount(3)).isSameInstanceAs(DISALLOW);
    assertThat(limiter.incrementAndCheckLogCount(3)).isSameInstanceAs(DISALLOW);
    // The fourth log is emitted (ignoring the case when there was no metadata).
    assertThat(limiter.incrementAndCheckLogCount(3)).isSameInstanceAs(allowStatus);
  }
}
