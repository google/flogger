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

package com.google.common.flogger.context;

import static com.google.common.flogger.testing.MetadataSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.flogger.MetadataKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ScopeMetadataTest {
  private static final MetadataKey<String> FOO_KEY = MetadataKey.single("FOO", String.class);
  private static final MetadataKey<String> BAR_KEY = MetadataKey.repeated("BAR", String.class);
  private static final MetadataKey<String> UNUSED_KEY = MetadataKey.single("UNUSED", String.class);

  @Test
  public void testNone() {
    assertThat(ScopeMetadata.none()).hasSize(0);
  }

  @Test
  public void testSingleton() {
    ScopeMetadata metadata = ScopeMetadata.singleton(FOO_KEY, "foo");
    assertThat(metadata).hasSize(1);
    assertThat(metadata).containsEntries(FOO_KEY, "foo");
    assertThat(metadata.findValue(UNUSED_KEY)).isNull();

  }

  @Test
  public void testBuilder() {
    ScopeMetadata metadata =
        ScopeMetadata.builder()
            .add(FOO_KEY, "one")
            .add(BAR_KEY, "two")
            .add(BAR_KEY, "three")
            .add(FOO_KEY, "four")
            .build();
    assertThat(metadata).hasSize(4);
    assertThat(metadata).containsEntries(FOO_KEY, "one", "four");
    assertThat(metadata).containsEntries(BAR_KEY, "two", "three");
    // The most recent single keyed value.
    assertThat(metadata.findValue(FOO_KEY)).isEqualTo("four");
    assertThat(metadata.findValue(UNUSED_KEY)).isNull();
    try {
      metadata.findValue(BAR_KEY);
      fail("expected IllegalArgumentException");
    } catch(IllegalArgumentException e) {
      // pass
    }
  }

  @Test
  public void testConcatenate_none() {
    ScopeMetadata metadata = ScopeMetadata.singleton(FOO_KEY, "foo");
    assertThat(ScopeMetadata.none().concatenate(metadata)).isSameInstanceAs(metadata);
    assertThat(metadata.concatenate(ScopeMetadata.none())).isSameInstanceAs(metadata);
  }

  @Test
  public void testConcatenate_duplicateSingleKey() {
    ScopeMetadata metadata =
        ScopeMetadata.singleton(FOO_KEY, "foo")
            .concatenate(ScopeMetadata.singleton(FOO_KEY, "bar"));
    assertThat(metadata).hasSize(2);
    // No reordering, no de-duplication.
    assertThat(metadata).containsEntries(FOO_KEY, "foo", "bar");
  }

  @Test
  public void testConcatenate_general() {
    ScopeMetadata first =
        ScopeMetadata.builder()
            .add(FOO_KEY, "one")
            .add(BAR_KEY, "two")
            .build();
    ScopeMetadata second =
        ScopeMetadata.builder()
            .add(BAR_KEY, "three")
            .add(FOO_KEY, "four")
            .build();
    ScopeMetadata metadata = first.concatenate(second);
    assertThat(metadata).hasSize(4);
    assertThat(metadata).containsEntries(FOO_KEY, "one", "four");
    assertThat(metadata).containsEntries(BAR_KEY, "two", "three");
  }
}
