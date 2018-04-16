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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.flogger.LogSiteStats.RateLimitPeriod;
import com.google.common.flogger.testing.FakeLogSite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogSiteStatsTest {

  @Test
  public void testGetStatsForKey() {
    LogSiteStats.StatsMap map = new LogSiteStats.StatsMap();

    LogSite logSite1 = FakeLogSite.create("class1", "method1", 1, "path1");
    LogSite logSite2 = FakeLogSite.create("class2", "method2", 2, "path2");

    LogSiteStats stats1 = map.getStatsForKey(logSite1);
    LogSiteStats stats2 = map.getStatsForKey(logSite2);

    assertThat(stats1).isNotNull();
    assertThat(stats2).isNotNull();
    assertThat(stats2).isNotSameAs(stats1);
    assertThat(map.getStatsForKey(logSite1)).isEqualTo(stats1);
    assertThat(map.getStatsForKey(logSite2)).isEqualTo(stats2);
  }

  @Test
  public void testIncrementAndCheckInvocationCount() {
    LogSiteStats stats = new LogSiteStats();

    // Always log for the first call.
    assertThat(stats.incrementAndCheckInvocationCount(2)).isTrue();

    // Alternating for a rate limit count of 2
    assertThat(stats.incrementAndCheckInvocationCount(2)).isFalse();
    assertThat(stats.incrementAndCheckInvocationCount(2)).isTrue();

    // Every third for a rate limit count of 3 (counter starts at 3, so returns true immediately).
    assertThat(stats.incrementAndCheckInvocationCount(3)).isTrue();
    assertThat(stats.incrementAndCheckInvocationCount(3)).isFalse();
    assertThat(stats.incrementAndCheckInvocationCount(3)).isFalse();
    assertThat(stats.incrementAndCheckInvocationCount(3)).isTrue();
  }

  @Test
  public void testCheckLastTimestamp() {
    LogSiteStats stats = new LogSiteStats();
    RateLimitPeriod period = LogSiteStats.newRateLimitPeriod(1, SECONDS);
    // Arbitrary start time (but within the first period to ensure we still log the first call).
    long startNanos = 123456000L;

    // Always log for the first call, but not again in the same period.
    assertThat(stats.checkLastTimestamp(startNanos, period)).isTrue();
    assertThat(period.toString()).isEqualTo("1 SECONDS");
    assertThat(stats.checkLastTimestamp(startNanos + MILLISECONDS.toNanos(500), period))
        .isFalse();

    // Return true exactly when next period begins.
    long nextStartNanos = startNanos + SECONDS.toNanos(1);
    assertThat(stats.checkLastTimestamp(nextStartNanos - 1, period)).isFalse();
    assertThat(stats.checkLastTimestamp(nextStartNanos, period)).isTrue();
    assertThat(period.toString()).isEqualTo("1 SECONDS [skipped: 2]");

    // Only return true once, even for duplicate calls.
    assertThat(stats.checkLastTimestamp(nextStartNanos, period)).isFalse();
  }

  @Test
  public void testPeriodToString() {
    assertThat(LogSiteStats.newRateLimitPeriod(23, SECONDS).toString()).isEqualTo("23 SECONDS");
  }
}
