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

import static com.google.common.flogger.RateLimitStatus.ALLOW;
import static com.google.common.flogger.RateLimitStatus.DISALLOW;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeMetadata;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RateLimitStatusTest {

  private static final class TestStatus extends RateLimitStatus {
    boolean wasReset = false;

    @Override
    public void reset() {
      wasReset = true;
    }
  }

  @Test
  public void testCombine_degenerateCases() {
    TestStatus fooStatus = new TestStatus();

    assertThat(RateLimitStatus.combine(null, null)).isNull();

    // null is returned by rate-limiters when the rate limiter is not used, and it is important to
    // distinguish that from an explicit "allow" status.
    assertThat(RateLimitStatus.combine(null, fooStatus)).isSameInstanceAs(fooStatus);
    assertThat(RateLimitStatus.combine(fooStatus, null)).isSameInstanceAs(fooStatus);

    // Not returning the "stateless" ALLOW sentinel is useful, but not strictly required.
    assertThat(RateLimitStatus.combine(ALLOW, fooStatus)).isSameInstanceAs(fooStatus);
    assertThat(RateLimitStatus.combine(fooStatus, ALLOW)).isSameInstanceAs(fooStatus);

    // Having "DISALLOW" be returned whenever present is essential for correctness.
    assertThat(RateLimitStatus.combine(DISALLOW, fooStatus)).isSameInstanceAs(DISALLOW);
    assertThat(RateLimitStatus.combine(fooStatus, DISALLOW)).isSameInstanceAs(DISALLOW);
  }

  @Test
  public void testCombine_multipleStatuses() {
    TestStatus fooStatus = new TestStatus();
    TestStatus barStatus = new TestStatus();

    assertThat(fooStatus.wasReset).isFalse();
    assertThat(barStatus.wasReset).isFalse();
    RateLimitStatus fooBarStatus = RateLimitStatus.combine(fooStatus, barStatus);

    fooBarStatus.reset();
    assertThat(fooStatus.wasReset).isTrue();
    assertThat(barStatus.wasReset).isTrue();
  }

  @Test
  public void testCombine_multipleStatuses_resetAlwaysCalled() {
    TestStatus fooStatus = new TestStatus();
    RateLimitStatus erroringStatus =
        new RateLimitStatus() {
          @Override
          public void reset() {
            throw new IllegalStateException("badness");
          }
        };
    assertThat(fooStatus.wasReset).isFalse();

    RateLimitStatus combinedStatus = RateLimitStatus.combine(fooStatus, erroringStatus);
    try {
      combinedStatus.reset();
      Assert.fail("expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Pass.
    }
    assertThat(fooStatus.wasReset).isTrue();

    // Same as above but just combining them in the opposite order.
    fooStatus.wasReset = false;
    combinedStatus = RateLimitStatus.combine(erroringStatus, fooStatus);
    try {
      combinedStatus.reset();
      Assert.fail("expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Pass.
    }
    assertThat(fooStatus.wasReset).isTrue();
  }

  @Test
  public void testCheckStatus_allowed() {
    FakeMetadata metadata = new FakeMetadata();
    // Use a different log site for each case to ensure the skip count is zero.

    // We wouldn't expect ALLOW to become the final status due to how combine() works, but we can
    // still test it.
    assertThat(RateLimitStatus.checkStatus(ALLOW, FakeLogSite.unique(), metadata)).isEqualTo(0);

    // Any (status != DISALLOW) will be reset as part of this call.
    TestStatus fooStatus = new TestStatus();
    assertThat(RateLimitStatus.checkStatus(fooStatus, FakeLogSite.unique(), metadata)).isEqualTo(0);
    assertThat(fooStatus.wasReset).isTrue();
  }

  @Test
  public void testCheckStatus_disallowed() {
    FakeMetadata metadata = new FakeMetadata();
    // Having DISALLOW is the most common case for rate-limited log statements.
    assertThat(RateLimitStatus.checkStatus(DISALLOW, FakeLogSite.unique(), metadata)).isEqualTo(-1);
  }

  @Test
  public void testCheckStatus_incrementsSkipCount() {
    FakeMetadata metadata = new FakeMetadata();
    LogSite logSite = FakeLogSite.unique();
    assertThat(RateLimitStatus.checkStatus(ALLOW, logSite, metadata)).isEqualTo(0);
    assertThat(RateLimitStatus.checkStatus(DISALLOW, logSite, metadata)).isEqualTo(-1);
    assertThat(RateLimitStatus.checkStatus(DISALLOW, logSite, metadata)).isEqualTo(-1);
    assertThat(RateLimitStatus.checkStatus(DISALLOW, logSite, metadata)).isEqualTo(-1);
    assertThat(RateLimitStatus.checkStatus(DISALLOW, logSite, metadata)).isEqualTo(-1);
    assertThat(RateLimitStatus.checkStatus(ALLOW, logSite, metadata)).isEqualTo(4);
  }
}
