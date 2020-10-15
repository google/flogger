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

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TagsTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static ImmutableSet<Object> setOf(Object... elements) {
    return ImmutableSet.copyOf(elements);
  }

  @Test
  public void testEmpty() {
    assertThat(Tags.builder().build()).isSameInstanceAs(Tags.empty());
    assertThat(Tags.empty().asMap()).isEmpty();
  }

  @Test
  public void testSimpleTag() {
    Tags tags = Tags.builder().addTag("foo").build();
    assertThat(tags.asMap()).containsEntry("foo", setOf());
    assertThat(tags.asMap()).hasSize(1);
  }

  @Test
  public void testTagWithString() {
    Tags tags = Tags.builder().addTag("foo", "bar").build();
    assertThat(tags.asMap()).containsEntry("foo", setOf("bar"));
    assertThat(tags.asMap()).hasSize(1);
  }

  @Test
  public void testTagWithEscapableString() {
    Tags tags = Tags.builder().addTag("foo", "\"foo\\bar\"").build();
    assertThat(tags.asMap()).containsEntry("foo", setOf("\"foo\\bar\""));
    assertThat(tags.asMap()).hasSize(1);
  }

  @Test
  public void testTagWithBoolean() {
    Tags tags = Tags.builder().addTag("foo", true).build();
    assertThat(tags.asMap()).containsEntry("foo", setOf(true));
    assertThat(tags.asMap()).hasSize(1);
  }

  @Test
  public void testTagWithLong() {
    Tags tags = Tags.builder().addTag("foo", 42L).build();
    assertThat(tags.asMap()).containsEntry("foo", setOf(42L));
    assertThat(tags.asMap()).hasSize(1);
  }

  @Test
  public void testTagWithDouble() {
    Tags tags = Tags.builder().addTag("foo", 12.34D).build();
    assertThat(tags.asMap()).containsEntry("foo", setOf(12.34D));
    assertThat(tags.asMap()).hasSize(1);
  }

  @Test
  public void testTagWithBadName() {
    thrown.expect(IllegalArgumentException.class);
    Tags.builder().addTag("foo!", "bar");
  }

  @Test
  public void testTagMerging_null() {
    Tags tags = Tags.builder().addTag("foo").build();

    thrown.expect(NullPointerException.class);
    tags.merge(null);
  }

  @Test
  public void testTagMerging_empty() {
    // It is important to not create new instances when merging.
    Tags tags = Tags.builder().addTag("foo").build();
    assertThat(tags.merge(Tags.empty())).isSameInstanceAs(tags);
    assertThat(Tags.empty().merge(tags)).isSameInstanceAs(tags);
    assertThat(Tags.empty().merge(Tags.empty())).isSameInstanceAs(Tags.empty());
  }

  @Test
  public void testTagMerging_distinct() {
    Tags lhs = Tags.builder().addTag("foo").addTag("tag", "true").addTag("tag", true).build();
    Tags rhs = Tags.builder().addTag("bar").addTag("tag", 42L).addTag("tag", 42.0D).build();
    Tags tags = lhs.merge(rhs);
    assertThat(tags.asMap()).containsEntry("foo", setOf());
    assertThat(tags.asMap()).containsEntry("bar", setOf());
    assertThat(tags.asMap()).containsEntry("tag", setOf("true", true, 42L, 42.0D));
    assertThat(tags.asMap()).hasSize(3);
  }

  @Test
  public void testTagMerging_overlap() {
    Tags lhs = Tags.builder().addTag("tag", "abc").addTag("tag", "def").build();
    Tags rhs = Tags.builder().addTag("tag", "abc").addTag("tag", "xyz").build();
    assertThat(lhs.merge(rhs).asMap()).containsEntry("tag", setOf("abc", "def", "xyz"));
    assertThat(rhs.merge(lhs).asMap()).containsEntry("tag", setOf("abc", "def", "xyz"));
  }

  @Test
  public void testTagMerging_superset() {
    Tags lhs = Tags.builder().addTag("tag", "abc").addTag("tag", "def").build();
    Tags rhs = Tags.builder().addTag("tag", "abc").build();
    assertThat(lhs.merge(rhs).asMap()).containsEntry("tag", setOf("abc", "def"));
    assertThat(rhs.merge(lhs).asMap()).containsEntry("tag", setOf("abc", "def"));
  }

  @Test
  public void testTagMerging_largeNumberOfKeys() {
    Tags.Builder lhs = Tags.builder();
    Tags.Builder rhs = Tags.builder();
    for (int i = 0; i < 256; i++) {
      String key = String.format("k%02X", i);
      if ((i & 1) == 0) {
        lhs.addTag(key);
      }
      if ((i & 2) == 0) {
        rhs.addTag(key);
      }
    }
    Map<String, Set<Object>> tagMap = lhs.build().merge(rhs.build()).asMap();
    assertThat(tagMap).hasSize(192);  // 3/4 of 256
    assertThat(tagMap.keySet())
        .containsAtLeast("k00", "k01", "k02", "k80", "kCC", "kFC", "kFD", "kFE")
        .inOrder();
    // Nothing ending in 3, 7, B or F.
    assertThat(tagMap.keySet()).containsNoneOf("k03", "k77", "kAB", "kFF");
  }

  @Test
  public void testTagMerging_largeNumberOfValues() {
    Tags.Builder lhs = Tags.builder();
    Tags.Builder rhs = Tags.builder();
    for (int i = 0; i < 256; i++) {
      String value = String.format("v%02X", i);
      if ((i & 1) == 0) {
        lhs.addTag("tag", value);
      }
      if ((i & 2) == 0) {
        rhs.addTag("tag", value);
      }
    }
    Map<String, Set<Object>> tagMap = lhs.build().merge(rhs.build()).asMap();
    assertThat(tagMap).hasSize(1);
    assertThat(tagMap).containsKey("tag");

    Set<Object> values = tagMap.get("tag");
    assertThat(values).hasSize(192);  // 3/4 of 256
    assertThat(values)
        .containsAtLeast("v00", "v01", "v02", "v80", "vCC", "vFC", "vFD", "vFE")
        .inOrder();
    assertThat(tagMap.keySet()).containsNoneOf("v03", "v77", "vAB", "vFF");
  }

  @Test
  public void testBuilder_largeNumberOfDuplicates() {
    Tags.Builder tags = Tags.builder();
    for (int i = 0; i < 256; i++) {
      tags.addTag("foo");
      tags.addTag("bar");
      for (int j = 0; j < 20; j++) {
        String value = "v" + (5 - (j % 5));  // v5 ... v1 (reverse order)
        tags.addTag("foo", value);
        tags.addTag("bar", value);
      }
    }
    Map<String, Set<Object>> tagMap = tags.build().asMap();
    assertThat(tagMap).hasSize(2);
    assertThat(tagMap.keySet()).containsExactly("bar", "foo").inOrder();
    assertThat(tagMap.get("foo")).containsExactly("v1", "v2", "v3", "v4", "v5").inOrder();
    assertThat(tagMap.get("bar")).containsExactly("v1", "v2", "v3", "v4", "v5").inOrder();
  }

  @Test
  public void testToString() {
    assertToString(Tags.builder(), "{}");
    assertToString(Tags.builder().addTag("foo").addTag("bar"), "{bar=[], foo=[]}");
    assertToString(Tags.builder().addTag("foo", "value"), "{foo=[value]}");
    assertToString(Tags.builder().addTag("foo", ""), "{foo=[]}");
    // Mixed types will be rare but should be sorted stably in the same order as the tag type enum:
    // boolean < string < integer < double
    assertToString(
        Tags.builder()
            .addTag("foo", "bar")
            .addTag("foo", true)
            .addTag("foo", 12.3)
            .addTag("foo", 42),
        "{foo=[true, bar, 42, 12.3]}");
  }

  private static void assertToString(Tags.Builder builder, String expected) {
    assertThat(builder.toString()).isEqualTo(expected);
    assertThat(builder.build().toString()).isEqualTo(expected);
  }
}
