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
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Range;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SamplingRateLimiterTest {
  @Test
  public void testInvalidCount() {
    Metadata metadata = new FakeMetadata().add(LOG_SAMPLE_EVERY_N, 0);
    assertThat(SamplingRateLimiter.check(metadata, FakeLogSite.unique())).isNull();
  }

  @Test
  public void testPendingCount() {
    SamplingRateLimiter limiter = new SamplingRateLimiter();
    // Initially we are not "pending", so disallow logging for an "impossible" sample rate.
    assertThat(limiter.pendingCount.get()).isEqualTo(0);
    assertThat(limiter.sampleOneIn(Integer.MAX_VALUE)).isEqualTo(RateLimitStatus.DISALLOW);
    for (int i = 0; i < 100; i++) {
      RateLimitStatus unused = limiter.sampleOneIn(5);
    }
    // Statistically we should be pending at least once.
    int pendingCount = limiter.pendingCount.get();
    assertThat(pendingCount).isGreaterThan(0);
    // Now we are pending, we allow logging even for an "impossible" sample rate.
    assertThat(limiter.sampleOneIn(Integer.MAX_VALUE)).isNotEqualTo(RateLimitStatus.DISALLOW);
    limiter.reset();
    assertThat(limiter.pendingCount.get()).isEqualTo(pendingCount - 1);
  }

  @Test
  public void testSamplingRate() {
    // Chance is less than one-millionth of 1% that this will fail spuriously.
    Metadata metadata = new FakeMetadata().add(LOG_SAMPLE_EVERY_N, 2);
    assertThat(countNSamples(1000, metadata)).isIn(Range.closed(400, 600));

    // Expected average is 20 logs out of 1000. Seeing 0 or > 100 is enormously unlikely.
    metadata = new FakeMetadata().add(LOG_SAMPLE_EVERY_N, 50);
    assertThat(countNSamples(1000, metadata)).isIn(Range.closed(1, 100));
  }

  private static int countNSamples(int n, Metadata metadata) {
    LogSite logSite = FakeLogSite.unique();
    int sampled = 0;
    while (n-- > 0) {
      if (RateLimitStatus.checkStatus(
          SamplingRateLimiter.check(metadata, logSite), logSite, metadata) >= 0) {
        sampled++;
      }
    }
    return sampled;
  }
}
