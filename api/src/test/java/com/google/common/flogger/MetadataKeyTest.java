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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.MetadataKey.KeyValueHandler;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.util.RecursionDepth;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class MetadataKeyTest {

  // A metadata key which simulates a situation where a custom key accidentally causes recursive
  // logging with itself (which is easy if the key is added to a context since all logging will now
  // include that key, even in code which has no explicit knowledge of it).
  private static final MetadataKey<Object> reentrant =
      new MetadataKey<Object>("reentrant", Object.class, true) {
        @Override
        protected void emit(Object value, KeyValueHandler kvh) {
          // Expected handling of value.
          kvh.handle("depth-" + Platform.getCurrentRecursionDepth(), "<<" + value + ">>");
          // This fakes the effect of recursively logging with the same metadata key.
          try (RecursionDepth fakeDepth = RecursionDepth.enterLogStatement()) {
            safeEmit(value, kvh);
          }
        }

        @Override
        protected void emitRepeated(Iterator<Object> values, KeyValueHandler kvh) {
          // Hack for test to preserve the given values past a single use. In normal logging there
          // would be a new Metadata instance created for each of the reentrant logging calls.
          ImmutableList<Object> copy = ImmutableList.copyOf(values);
          // Expected handling of value.
          kvh.handle("depth-" + Platform.getCurrentRecursionDepth(), copy);
          // This fakes the effect of recursively logging with the same metadata key.
          try (RecursionDepth fakeDepth = RecursionDepth.enterLogStatement()) {
            safeEmitRepeated(copy.iterator(), kvh);
          }
        }
      };

  private static final class AppendingHandler implements KeyValueHandler {
    final ArrayList<String> entries = new ArrayList<>();

    @Override
    public void handle(String label, Object value) {
      entries.add(label + "=" + value);
    }
  }

  private static <T> Iterator<T> iterate(T... values) {
    return ImmutableList.copyOf(values).iterator();
  }

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
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void testCasting() {
    MetadataKey<String> k = MetadataKey.single("foo", String.class);
    assertThat(k.cast("value")).isEqualTo("value");
    try {
      k.cast(123);
      fail("expected ClassCastException");
    } catch (ClassCastException expected) {
    }
  }

  @Test
  public void testDefaultEmit() {
    MetadataKey<String> k = MetadataKey.single("foo", String.class);
    KeyValueHandler handler = Mockito.mock(KeyValueHandler.class);
    k.safeEmit("123", handler);
    Mockito.verify(handler).handle("foo", "123");
  }

  @Test
  public void testDefaultEmitRepeated() {
    MetadataKey<String> k = MetadataKey.repeated("foo", String.class);
    KeyValueHandler handler = Mockito.mock(KeyValueHandler.class);
    k.safeEmitRepeated(iterate("123", "abc"), handler);
    Mockito.verify(handler).handle("foo", "123");
    Mockito.verify(handler).handle("foo", "abc");
  }

  @Test
  public void testDefaultEmitRepeated_singleKeyFails() {
    MetadataKey<String> k = MetadataKey.single("foo", String.class);
    KeyValueHandler handler = Mockito.mock(KeyValueHandler.class);
    try {
      k.safeEmitRepeated(iterate("123", "abc"), handler);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testReentrantLoggingProtection_single() {
    AppendingHandler handler = new AppendingHandler();
    reentrant.safeEmit("value", handler);

    // Max recursion is 20 (see MetadataKey#MAX_CUSTOM_METADATAKEY_RECURSION_DEPTH) but the initial
    // log statement has no recursion, so 21 possible logs before mitigation occurs.
    List<String> expected = new ArrayList<>();
    for (int n = 0; n <= 20; n++) {
      expected.add("depth-" + n + "=<<value>>");
    }
    // The non-customized key/value representation is emitted when recursion is halted.
    expected.add("reentrant=value");

    assertThat(handler.entries).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void testReentrantLoggingProtection_repeated() {
    AppendingHandler handler = new AppendingHandler();
    reentrant.safeEmitRepeated(iterate("foo", "bar"), handler);

    // Max recursion is 20 (see MetadataKeyValueHandlers#MAX_CUSTOM_METADATAKEY_RECURSION_DEPTH) but
    // the initial log statement has no recursion, so 21 possible logs before mitigation occurs.
    List<String> expected = new ArrayList<>();
    for (int n = 0; n <= 20; n++) {
      expected.add("depth-" + n + "=[foo, bar]");
    }
    // The non-customized key/value representation is emitted when recursion is halted (in the case
    // of repeated values, that's two entries due to how the fake handler is written; this is fine).
    expected.add("reentrant=foo");
    expected.add("reentrant=bar");

    assertThat(handler.entries).containsExactlyElementsIn(expected).inOrder();
  }

  @Test
  public void testNulls() {
    try {
      MetadataKey.single(null, String.class);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
    try {
      MetadataKey.repeated(null, String.class);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
    try {
      new MetadataKey<>(null, String.class, false);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }

    try {
      MetadataKey.single("label", null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
    try {
      MetadataKey.repeated("label", null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
    try {
      new MetadataKey<>("label", null, false);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }
}
