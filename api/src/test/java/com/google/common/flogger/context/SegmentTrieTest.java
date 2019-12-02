/*
 * Copyright (C) 2019 The Flogger Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SegmentTrieTest {
  private static final String DEFAULT = "DEFAULT";

  @Test
  public void testEmptyMap() {
    Map<String, String> map = ImmutableMap.of();
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("")).isEqualTo(DEFAULT);
    assertThat(trie.find(".")).isEqualTo(DEFAULT);
    assertThat(trie.find("foo")).isEqualTo(DEFAULT);
  }

  @Test
  public void testSingletonMap() {
    Map<String, String> map = ImmutableMap.of("com.foo", "FOO");
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("com.foo")).isEqualTo("FOO");
    assertThat(trie.find("com.foo.xxx")).isEqualTo("FOO");

    assertThat(trie.find("")).isEqualTo(DEFAULT);
    assertThat(trie.find("com")).isEqualTo(DEFAULT);
    assertThat(trie.find("com.foobar")).isEqualTo(DEFAULT);
    assertThat(trie.find("xxx")).isEqualTo(DEFAULT);
  }

  @Test
  public void testSingletonMap_emptysegments() {
    Map<String, String> map = ImmutableMap.of("...", "DOT");
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("...")).isEqualTo("DOT");
    assertThat(trie.find("....")).isEqualTo("DOT");
    assertThat(trie.find(".....")).isEqualTo("DOT");
    assertThat(trie.find("....x")).isEqualTo("DOT");

    assertThat(trie.find("")).isEqualTo(DEFAULT);
    assertThat(trie.find(".")).isEqualTo(DEFAULT);
    assertThat(trie.find("..")).isEqualTo(DEFAULT);
    assertThat(trie.find("x...")).isEqualTo(DEFAULT);
    assertThat(trie.find(".x..")).isEqualTo(DEFAULT);
    assertThat(trie.find("..x.")).isEqualTo(DEFAULT);
    assertThat(trie.find("...x")).isEqualTo(DEFAULT);
  }

  @Test
  public void testSingletonMap_emptykey() {
    Map<String, String> map = ImmutableMap.of("", "FOO");
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("")).isEqualTo("FOO");
    assertThat(trie.find(".")).isEqualTo("FOO");
    assertThat(trie.find("..")).isEqualTo("FOO");
    assertThat(trie.find(".x")).isEqualTo("FOO");

    assertThat(trie.find("x")).isEqualTo(DEFAULT);
    assertThat(trie.find("x.")).isEqualTo(DEFAULT);
    assertThat(trie.find("x..")).isEqualTo(DEFAULT);
    assertThat(trie.find("x.y")).isEqualTo(DEFAULT);
  }

  @Test
  public void testSingletonMap_nullkey() {
    Map<String, String> map = new HashMap<>();
    map.put(null, "BAD");
    try {
      @SuppressWarnings("unused")
      SegmentTrie<String> unused = SegmentTrie.create(map, '.', DEFAULT);
      fail("expected NullPointerException");
    } catch (NullPointerException e) {
      // pass
    }
  }

  @Test
  public void testSingletonMap_nullvalue() {
    Map<String, String> map = new HashMap<>();
    map.put("com.foo", null);
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("com.foo")).isNull();
    assertThat(trie.find("com.foo.xxx")).isNull();

    assertThat(trie.find("com")).isEqualTo(DEFAULT);
    assertThat(trie.find("com.foobar")).isEqualTo(DEFAULT);
    assertThat(trie.find("xxx")).isEqualTo(DEFAULT);
  }

  @Test
  public void testGeneralCaseMap() {
    Map<String, String> map =
        ImmutableMap.of(
            "com.bar", "BAR",
            "com.foo", "FOO",
            "com.foo.bar", "FOO_BAR",
            "com.quux", "QUUX");
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("com.bar")).isEqualTo("BAR");
    assertThat(trie.find("com.bar.xxx")).isEqualTo("BAR");
    assertThat(trie.find("com.foo")).isEqualTo("FOO");
    assertThat(trie.find("com.foo.xxx")).isEqualTo("FOO");
    assertThat(trie.find("com.foo.barf")).isEqualTo("FOO");
    assertThat(trie.find("com.foo.bar")).isEqualTo("FOO_BAR");
    assertThat(trie.find("com.foo.bar.quux")).isEqualTo("FOO_BAR");

    assertThat(trie.find("")).isEqualTo(DEFAULT);
    assertThat(trie.find("com")).isEqualTo(DEFAULT);
    assertThat(trie.find("com.foobar")).isEqualTo(DEFAULT);
    assertThat(trie.find("xxx")).isEqualTo(DEFAULT);
  }

  @Test
  public void testGeneralCaseMap_emptysegments() {
    Map<String, String> map =
        ImmutableMap.of(
            "", "EMPTY",
            ".", "DOT",
            "..", "DOT_DOT",
            ".foo.", "FOO");
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("")).isEqualTo("EMPTY");
    assertThat(trie.find(".foo")).isEqualTo("EMPTY");
    assertThat(trie.find(".foo.bar")).isEqualTo("EMPTY");
    assertThat(trie.find(".")).isEqualTo("DOT");
    assertThat(trie.find("..foo")).isEqualTo("DOT");
    assertThat(trie.find("...foo")).isEqualTo("DOT_DOT");
    assertThat(trie.find(".foo..bar")).isEqualTo("FOO");

    assertThat(trie.find("foo")).isEqualTo(DEFAULT);
    assertThat(trie.find("foo.bar")).isEqualTo(DEFAULT);
  }

  @Test
  public void testGeneralCaseMap_nullkeys() {
    Map<String, String> map = new HashMap<>();
    map.put("foo", "FOO");
    map.put(null, "BAD");
    try {
      @SuppressWarnings("unused")
      SegmentTrie<String> unused = SegmentTrie.create(map, '.', DEFAULT);
      fail("expected NullPointerException");
    } catch (NullPointerException e) {
      // pass
    }
  }

  @Test
  public void testGeneralCaseMap_nullvalues() {
    Map<String, String> map = new HashMap<>();
    map.put("foo", null);
    map.put("foo.bar", "FOO_BAR");
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("foo.bar")).isEqualTo("FOO_BAR");

    assertThat(trie.find("foo")).isNull();
    assertThat(trie.find("foo.")).isNull();
    assertThat(trie.find("foo.barf")).isNull();

    assertThat(trie.find("")).isEqualTo(DEFAULT);
    assertThat(trie.find("foo_bar")).isEqualTo(DEFAULT);
  }

  @Test
  public void testImmutable() {
    Map<String, String> map = new HashMap<>();
    map.put("foo", "FOO");
    SegmentTrie<String> trie = SegmentTrie.create(map, '.', DEFAULT);

    assertThat(trie.find("foo.bar")).isEqualTo("FOO");

    // No change if source map modified.
    map.put("foo.bar", "BAR");
    assertThat(trie.find("foo.bar")).isEqualTo("FOO");
  }
}
