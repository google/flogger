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

package com.google.common.flogger.backend;

import static com.google.common.flogger.backend.Metadata.empty;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.testing.FakeMetadata;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetadataHandlerTest {
  @Test
  public void testUnknownValue() {
    MetadataKey<String> unknownKey = MetadataKey.single("unknown", String.class);

    MetadataHandler<StringBuilder> handler =
        MetadataHandler.builder(MetadataHandlerTest::appendUnknownValue).build();

    FakeMetadata scope = new FakeMetadata();
    scope.add(unknownKey, "hello");

    assertMetadata(handler, scope, empty(), "unknown=<<hello>>");
  }

  @Test
  public void testSingleHandler() {
    MetadataKey<String> key = MetadataKey.single("key", String.class);
    MetadataKey<String> rep = MetadataKey.repeated("rep", String.class);

    MetadataHandler<StringBuilder> handler =
        MetadataHandler.builder(MetadataHandlerTest::appendUnknownValue)
            .addHandler(key, MetadataHandlerTest::appendValue)
            .addHandler(rep, MetadataHandlerTest::appendValue)
            .build();

    FakeMetadata scope = new FakeMetadata();
    scope.add(key, "hello");
    scope.add(rep, "repeated");
    scope.add(rep, "world");

    assertMetadata(handler, scope, empty(), "key=hello rep=repeated rep=world");
  }

  @Test
  public void testRepeatedHandler() {
    MetadataKey<String> key = MetadataKey.repeated("key", String.class);

    MetadataHandler<StringBuilder> handler =
        MetadataHandler.builder(MetadataHandlerTest::appendUnknownValue)
            .addRepeatedHandler(key, MetadataHandlerTest::appendValues)
            .build();

    FakeMetadata scope = new FakeMetadata();
    scope.add(key, "hello");
    scope.add(key, "world");

    assertMetadata(handler, scope, empty(), "key=[hello, world]");
  }

  @Test
  public void testDefaultRepeatedHandler() {
    MetadataKey<String> fooKey = MetadataKey.repeated("foo", String.class);
    MetadataKey<Integer> barKey = MetadataKey.repeated("bar", Integer.class);

    MetadataHandler<StringBuilder> handler =
        MetadataHandler.builder(MetadataHandlerTest::appendUnknownValue)
            .setDefaultRepeatedHandler(MetadataHandlerTest::appendUnknownValues)
            // Adding an explicit individual handler takes precedence.
            .addHandler(fooKey, MetadataHandlerTest::appendValue)
            .build();

    FakeMetadata scope = new FakeMetadata();
    scope.add(fooKey, "hello");
    scope.add(barKey, 13);
    scope.add(barKey, 20);

    FakeMetadata logged = new FakeMetadata();
    logged.add(barKey, 9);
    logged.add(fooKey, "world");

    assertMetadata(handler, scope, logged, "foo=hello foo=world bar=<<13, 20, 9>>");
  }

  @Test
  public void testMultipleHandlers() {
    MetadataKey<String> fooKey = MetadataKey.repeated("foo", String.class);
    MetadataKey<Integer> barKey = MetadataKey.repeated("bar", Integer.class);
    MetadataKey<String> unknownKey = MetadataKey.single("baz", String.class);

    MetadataHandler<StringBuilder> handler =
        MetadataHandler.builder(MetadataHandlerTest::appendUnknownValue)
            .addRepeatedHandler(barKey, MetadataHandlerTest::appendSum)
            .addHandler(fooKey, MetadataHandlerTest::appendValue)
            .build();

    FakeMetadata scope = new FakeMetadata();
    scope.add(fooKey, "hello");
    scope.add(barKey, 13);
    scope.add(unknownKey, "unknown");
    scope.add(barKey, 20);

    FakeMetadata logged = new FakeMetadata();
    logged.add(barKey, 9);
    logged.add(fooKey, "world");

    assertMetadata(handler, scope, logged, "foo=hello foo=world sum(bar)=42 baz=<<unknown>>");
  }

  @Test
  public void testDuplicateHandlers() {
    MetadataKey<String> key = MetadataKey.repeated("key", String.class);

    MetadataHandler<StringBuilder> handler =
        MetadataHandler.builder(MetadataHandlerTest::appendUnknownValue)
            .addRepeatedHandler(key, MetadataHandlerTest::appendValues)
            .addHandler(key, MetadataHandlerTest::appendValue)
            .build();

    FakeMetadata scope = new FakeMetadata();
    scope.add(key, "hello");
    scope.add(key, "world");

    assertMetadata(handler, scope, empty(), "key=hello key=world");
  }

  @Test
  public void testRemoveHandlers() {
    MetadataKey<String> key = MetadataKey.repeated("key", String.class);

    MetadataHandler.Builder<StringBuilder> builder =
        MetadataHandler.builder(MetadataHandlerTest::appendUnknownValue)
            .addRepeatedHandler(key, MetadataHandlerTest::appendValues);

    FakeMetadata scope = new FakeMetadata();
    scope.add(key, "hello");
    scope.add(key, "world");

    assertMetadata(builder.build(), scope, empty(), "key=[hello, world]");
    assertMetadata(
        builder.removeHandlers(key).build(), scope, empty(), "key=<<hello>> key=<<world>>");
  }

  private static void assertMetadata(
      MetadataHandler<StringBuilder> handler, FakeMetadata scope, Metadata empty, String s) {
    StringBuilder buf = new StringBuilder();
    MetadataProcessor.forScopeAndLogSite(scope, empty).process(handler, buf);
    assertThat(buf.toString().trim()).isEqualTo(s);
  }

  private static void appendUnknownValue(MetadataKey<?> k, Object v, StringBuilder c) {
    c.append(k.getLabel()).append("=<<").append(v).append(">> ");
  }

  private static void appendUnknownValues(MetadataKey<?> k, Iterator<?> v, StringBuilder c) {
    appendUnknownValue(k, Joiner.on(", ").join(v), c);
  }

  private static void appendValue(MetadataKey<?> k, Object v, StringBuilder c) {
    c.append(k.getLabel()).append("=").append(v).append(" ");
  }

  private static void appendValues(MetadataKey<?> k, Iterator<?> v, StringBuilder c) {
    appendValue(k, Iterators.toString(v), c);
  }

  private static void appendSum(MetadataKey<Integer> k, Iterator<Integer> v, StringBuilder c) {
    int n = 0;
    while (v.hasNext()) {
      n += v.next();
    }
    c.append("sum(").append(k.getLabel()).append(")=").append(n).append(" ");
  }
}
