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

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogPerBucketingStrategyTest {
  @Test
  public void knownBounded() {
    Object anyKey = new Object();
    assertThat(LogPerBucketingStrategy.knownBounded().apply(anyKey)).isSameInstanceAs(anyKey);
    assertThat(LogPerBucketingStrategy.knownBounded().toString())
        .isEqualTo("LogPerBucketingStrategy[KnownBounded]");
  }

  @Test
  public void byClass() {
    Object anyKey = new Object();
    assertThat(LogPerBucketingStrategy.byClass().apply(anyKey)).isSameInstanceAs(anyKey.getClass());
    assertThat(LogPerBucketingStrategy.byClass().toString())
        .isEqualTo("LogPerBucketingStrategy[ByClass]");
  }

  static final class NotASystemClass {}

  @Test
  public void byClassName() {
    Object anyKey = new NotASystemClass();
    assertThat(LogPerBucketingStrategy.byClassName().apply(anyKey))
        .isSameInstanceAs("com.google.common.flogger.LogPerBucketingStrategyTest$NotASystemClass");
    assertThat(LogPerBucketingStrategy.byClassName().toString())
        .isEqualTo("LogPerBucketingStrategy[ByClassName]");
  }

  @Test
  public void forKnownKeys() {
    LogPerBucketingStrategy<Object> strategy =
        LogPerBucketingStrategy.forKnownKeys(Arrays.asList("foo", 23));
    assertThat(strategy.apply("foo")).isEqualTo(0);
    assertThat(strategy.apply("bar")).isNull();
    // Default boxing rules apply.
    assertThat(strategy.apply(23)).isEqualTo(1);
    assertThat(strategy.apply(23.0)).isNull();
    assertThat(strategy.toString()).isEqualTo("LogPerBucketingStrategy[ForKnownKeys(foo, 23)]");
  }

  @Test
  public void byHashcode() {
    Object key =
        new Object() {
          @Override
          public int hashCode() {
            return -1;
          }
        };
    // Show that the strategy choice changes the bucketed value as expected. To maximize the Integer
    // caching done by the JVM, the expected value has 128 subtracted from it.
    assertThat(LogPerBucketingStrategy.byHashCode(1).apply(key)).isSameInstanceAs(-128);
    assertThat(LogPerBucketingStrategy.byHashCode(30).apply(key)).isSameInstanceAs(29 - 128);
    assertThat(LogPerBucketingStrategy.byHashCode(10).apply(key)).isSameInstanceAs(9 - 128);
    // Max cached value is 127 (corresponding to a modulo of 255).
    assertThat(LogPerBucketingStrategy.byHashCode(256).apply(key)).isSameInstanceAs(127);
    // Above this we cannot assume singleton semantics.
    assertThat(LogPerBucketingStrategy.byHashCode(257).apply(key)).isEqualTo(128);

    assertThat(LogPerBucketingStrategy.byHashCode(10).toString())
        .isEqualTo("LogPerBucketingStrategy[ByHashCode(10)]");
  }
}
