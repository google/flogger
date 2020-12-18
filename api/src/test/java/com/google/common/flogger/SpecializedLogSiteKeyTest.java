/*
 * Copyright (C) 2020 The Flogger Authors.
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

import com.google.common.flogger.testing.FakeLogSite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpecializedLogSiteKeyTest {

  @Test
  public void testEqualsAndHashCode() {
    LogSite logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>");
    LogSiteKey fooKey = SpecializedLogSiteKey.of(logSite, "foo");

    assertThat(SpecializedLogSiteKey.of(logSite, "foo")).isEqualTo(fooKey);
    assertThat(SpecializedLogSiteKey.of(logSite, "foo").hashCode()).isEqualTo(fooKey.hashCode());

    assertThat(SpecializedLogSiteKey.of(logSite, "bar")).isNotEqualTo(fooKey);
    assertThat(SpecializedLogSiteKey.of(logSite, "bar").hashCode()).isNotEqualTo(fooKey.hashCode());

    LogSite otherLogSite = FakeLogSite.create("com.google.foo.Bar", "doOther", 23, "<unused>");
    assertThat(SpecializedLogSiteKey.of(otherLogSite, "foo")).isNotEqualTo(fooKey);
    assertThat(SpecializedLogSiteKey.of(otherLogSite, "foo").hashCode())
        .isNotEqualTo(fooKey.hashCode());
  }

  // Conceptually order does not matter, but it is hard to make equals work efficiently and be
  // order invariant. However having two or more specializations on a key will almost never happen,
  // and even if it does, the metadata preserves order at the log site, so keys should be the same
  // each time.
  // TODO: Consider making equality invariant to specialization order if it can be done efficiently.
  @Test
  public void testSpecializationOrderMatters() {
    LogSite logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>");
    LogSiteKey fooBarKey =
        SpecializedLogSiteKey.of(SpecializedLogSiteKey.of(logSite, "foo"), "bar");
    LogSiteKey barFooKey =
        SpecializedLogSiteKey.of(SpecializedLogSiteKey.of(logSite, "bar"), "foo");
    assertThat(fooBarKey).isNotEqualTo(barFooKey);
  }
}
