/*
 * Copyright (C) 2021 The Flogger Authors.
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

package com.google.common.flogger.backend;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.MetadataKey.KeyValueHandler;
import java.util.ArrayList;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MetadataKeyValueHandlersTest {
  private static final MetadataKey<Object> single = MetadataKey.single("single", Object.class);
  private static final MetadataKey<Object> repeated =
      MetadataKey.repeated("repeated", Object.class);
  private static final MetadataKey<Object> ignored = MetadataKey.single("ignored", Object.class);

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
  public void testDefaultValueHandler() {
    AppendingHandler handler = new AppendingHandler();
    MetadataKeyValueHandlers.getDefaultValueHandler().handle(single, "value", handler);
    assertThat(handler.entries).containsExactly("single=value");
  }

  @Test
  public void testDefaultRepeatedValueHandler() {
    AppendingHandler handler = new AppendingHandler();
    MetadataKeyValueHandlers.getDefaultRepeatedValueHandler()
        .handle(repeated, iterate("foo", "bar"), handler);
    assertThat(handler.entries).containsExactly("repeated=foo", "repeated=bar").inOrder();
  }

  @Test
  public void testDefaultHandler_ignoresSpecifiedKeys() {
    AppendingHandler handler = new AppendingHandler();
    MetadataHandler<KeyValueHandler> metadataHandler =
        MetadataKeyValueHandlers.getDefaultHandler(ImmutableSet.of(ignored));
    metadataHandler.handle(single, "foo", handler);
    metadataHandler.handle(ignored, "ignored", handler);
    metadataHandler.handleRepeated(repeated, iterate("bar", "baz"), handler);
    assertThat(handler.entries)
        .containsExactly("single=foo", "repeated=bar", "repeated=baz")
        .inOrder();
  }
}
