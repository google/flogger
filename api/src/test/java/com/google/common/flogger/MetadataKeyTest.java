/*
 * Copyright (C) 2018 The Flogger Authors.
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
import static java.util.Arrays.asList;
import static org.junit.Assert.fail;

import com.google.common.flogger.backend.KeyValueHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class MetadataKeyTest {
  @Test
  public void testLabel() {
    // Good labels
    for (String label : asList("foo", "foo_bar", "FooBar")) {
      MetadataKey<String> k = MetadataKey.single(label, String.class);
      assertThat(k.getLabel()).isEqualTo(label);
    }
    // Bad labels
    for (String label : asList("", "foo bar", "_FOO")) {
      try {
        new MetadataKey<>(label, String.class, false);
        fail("expected IllegalArgumentException");
      } catch (IllegalArgumentException expected) { }
    }
  }

  @Test
  public void testCasting() {
    MetadataKey<String> k = MetadataKey.single("foo", String.class);
    assertThat(k.cast("value")).isEqualTo("value");
    try {
      k.cast(123);
      fail("expected ClassCastException");
    } catch (ClassCastException expected) { }
  }

  @Test
  public void testDefaultEmit() {
    MetadataKey<String> k = MetadataKey.single("foo", String.class);
    KeyValueHandler handler = Mockito.mock(KeyValueHandler.class);
    k.emit(123, handler);
    Mockito.verify(handler).handle("foo", 123);
  }

  @Test
  public void testNulls() {
    try {
      MetadataKey.single(null, String.class);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) { }
    try {
      MetadataKey.repeated(null, String.class);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) { }
    try {
      new MetadataKey<>(null, String.class, false);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) { }

    try {
      MetadataKey.single("label", null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) { }
    try {
      MetadataKey.repeated("label", null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) { }
    try {
      new MetadataKey<>("label", null, false);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) { }
  }
}
