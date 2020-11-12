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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.Iterators;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.testing.FakeMetadata;
import com.google.common.truth.Expect;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetadataProcessorTest {
  private static final MetadataKey<String> KEY_1 = MetadataKey.single("K1", String.class);
  private static final MetadataKey<String> KEY_2 = MetadataKey.single("K2", String.class);
  private static final MetadataKey<String> KEY_3 = MetadataKey.single("K3", String.class);
  private static final MetadataKey<String> REP_1 = MetadataKey.repeated("R1", String.class);
  private static final MetadataKey<String> REP_2 = MetadataKey.repeated("R2", String.class);

  @Rule public final Expect expect = Expect.create();

  @Test
  public void testSimpleCombinedMetadata() {
    FakeMetadata scope = new FakeMetadata();
    scope.add(KEY_1, "one");
    scope.add(KEY_2, "two");

    FakeMetadata logged = new FakeMetadata();
    logged.add(KEY_3, "three");

    assertMetadata(scope, logged, "K1=one", "K2=two", "K3=three");
  }

  @Test
  public void testSimpleRepeated() {
    FakeMetadata scope = new FakeMetadata();
    scope.add(REP_1, "first");
    scope.add(KEY_1, "single");
    scope.add(REP_1, "second");

    FakeMetadata logged = new FakeMetadata();
    logged.add(REP_1, "third");

    assertMetadata(scope, logged, "R1=[first, second, third]", "K1=single");
  }

  @Test
  public void testMessy() {
    FakeMetadata scope = new FakeMetadata();
    scope.add(KEY_1, "original");
    scope.add(REP_1, "r1-1");
    scope.add(REP_2, "r2-1");
    scope.add(REP_1, "r1-2");

    FakeMetadata logged = new FakeMetadata();
    logged.add(REP_2, "r2-2");
    logged.add(KEY_2, "value");
    logged.add(REP_2, "r2-1"); // Duplicated from scope.
    logged.add(KEY_1, "override");

    assertMetadata(
        scope, logged, "K1=override", "R1=[r1-1, r1-2]", "R2=[r2-1, r2-2, r2-1]", "K2=value");
  }

  @Test
  public void testMaxLightweight() {
    // Max entries is 28 for lightweight processor.
    FakeMetadata scope = new FakeMetadata();
    for (int n = 0; n < 28; n++) {
      MetadataKey<String> k = (n & 1) == 0 ? REP_1 : REP_2;
      scope.add(k, "v" + n);
    }

    assertMetadata(
        scope,
        Metadata.empty(),
        "R1=[v0, v2, v4, v6, v8, v10, v12, v14, v16, v18, v20, v22, v24, v26]",
        "R2=[v1, v3, v5, v7, v9, v11, v13, v15, v17, v19, v21, v23, v25, v27]");
  }

  @Test
  public void testAllDistinctKeys() {
    // Max entries is 28 for lightweight processor. With all distinct keys you are bound to force
    // at least one false positive in the bloom filter in the lightweight processor.
    FakeMetadata scope = new FakeMetadata();
    for (int n = 0; n < 28; n++) {
      scope.add(MetadataKey.single("K" + n, String.class), "v" + n);
    }
    assertMetadata(
        scope,
        Metadata.empty(),
        "K0=v0",
        "K1=v1",
        "K2=v2",
        "K3=v3",
        "K4=v4",
        "K5=v5",
        "K6=v6",
        "K7=v7",
        "K8=v8",
        "K9=v9",
        "K10=v10",
        "K11=v11",
        "K12=v12",
        "K13=v13",
        "K14=v14",
        "K15=v15",
        "K16=v16",
        "K17=v17",
        "K18=v18",
        "K19=v19",
        "K20=v20",
        "K21=v21",
        "K22=v22",
        "K23=v23",
        "K24=v24",
        "K25=v25",
        "K26=v26",
        "K27=v27");
  }

  @Test
  public void testWorstCaseLookup() {
    // Since duplicated keys need to have their index looked up (linear scan) the worst case
    // scenario for performance in 14 distinct keys followed by the same repeated key 14 times.
    // This means that there are (N/2)^2 key accesses.
    FakeMetadata scope = new FakeMetadata();
    for (int n = 0; n < 14; n++) {
      scope.add(MetadataKey.single("K" + n, String.class), "v" + n);
    }
    for (int n = 14; n < 28; n++) {
      scope.add(REP_1, "v" + n);
    }

    assertMetadata(
        scope,
        Metadata.empty(),
        "K0=v0",
        "K1=v1",
        "K2=v2",
        "K3=v3",
        "K4=v4",
        "K5=v5",
        "K6=v6",
        "K7=v7",
        "K8=v8",
        "K9=v9",
        "K10=v10",
        "K11=v11",
        "K12=v12",
        "K13=v13",
        "R1=[v14, v15, v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26, v27]");
  }

  @Test
  public void testSingleKeyHandling() {
    FakeMetadata scope = new FakeMetadata();
    scope.add(REP_1, "first");
    scope.add(KEY_1, "single");
    scope.add(REP_1, "second");

    FakeMetadata logged = new FakeMetadata();
    logged.add(REP_1, "third");

    assertSingleKey(REP_1, scope, logged, "R1=[first, second, third]");
    assertSingleKey(KEY_1, scope, logged, "K1=single");
    assertSingleKey(KEY_3, scope, logged, null);
  }

  @Test
  public void testReadOnlyIterable() {
    FakeMetadata scope = new FakeMetadata();
    scope.add(REP_1, "one");
    scope.add(REP_1, "two");

    MetadataHandler<Void> handler = new MetadataHandler<Void>() {
      @Override
      protected <T> void handle(MetadataKey<T> key, T value, Void context) { }

      @Override
      protected <T> void handleRepeated(MetadataKey<T> key, Iterator<T> values, Void context) {
        assertThat(values.hasNext()).isTrue();
        assertThat(values.next()).isEqualTo("one");
        values.remove();
      }
    };

    try {
      MetadataProcessor.getLightweightProcessor(scope, Metadata.empty()).process(handler, null);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // pass
    }

    try {
      MetadataProcessor.getSimpleProcessor(scope, Metadata.empty()).process(handler, null);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // pass
    }
  }

  private void assertMetadata(Metadata scope, Metadata logged, String... expected) {
    assertMetadataProcessor(
        "Lightweight Processor",
        MetadataProcessor.getLightweightProcessor(scope, logged),
        expected);
    assertMetadataProcessor(
        "Simple Processor", MetadataProcessor.getSimpleProcessor(scope, logged), expected);
  }

  private void assertMetadataProcessor(
      String message, MetadataProcessor processor, String[] expected) {
    List<String> entries = new ArrayList<>();
    processor.process(new CollectingHandler(), entries);
    expect.withMessage(message).that(entries).containsExactlyElementsIn(expected).inOrder();
  }

  private void assertSingleKey(
      MetadataKey<?> key, Metadata scope, Metadata logged, String expected) {
    assertSingleKey(
        "Lightweight Processor",
        key,
        MetadataProcessor.getLightweightProcessor(scope, logged),
        expected);
    assertSingleKey(
        "Simple Processor", key, MetadataProcessor.getSimpleProcessor(scope, logged), expected);
  }

  private void assertSingleKey(
      String message, MetadataKey<?> key, MetadataProcessor processor, String expected) {
    List<String> entries = new ArrayList<>();
    processor.handle(key, new CollectingHandler(), entries);
    if (expected != null) {
      expect.withMessage(message).that(entries).containsExactly(expected);
    } else {
      expect.withMessage(message).that(entries).isEmpty();
    }
  }

  private static class CollectingHandler extends MetadataHandler<List<String>> {
    @Override
    protected <T> void handle(MetadataKey<T> key, T value, List<String> out) {
      out.add(String.format("%s=%s", key.getLabel(), value));
    }

    @Override
    protected <T> void handleRepeated(MetadataKey<T> key, Iterator<T> values, List<String> out) {
      out.add(String.format("%s=%s", key.getLabel(), Iterators.toString(values)));
    }
  }
}
