/*
 * Copyright (C) 2016 The Flogger Authors.
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

import com.google.common.collect.ImmutableSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class TagsTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static ImmutableSet<Object> setOf(Object... elements) {
    return ImmutableSet.copyOf(elements);
  }

  @Test
  public void testEmpty() {
    assertThat(Tags.builder().build()).isSameAs(Tags.empty());
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
    assertThat(tags.merge(Tags.empty())).isSameAs(tags);
    assertThat(Tags.empty().merge(tags)).isSameAs(tags);
    assertThat(Tags.empty().merge(Tags.empty())).isSameAs(Tags.empty());
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
  public void testEmitAllOrdering() {
    Tags tags = Tags.builder().addTag("foo", true).addTag("bar", 42).addTag("baz").build();
    KeyValueHandler handler = Mockito.mock(KeyValueHandler.class);
    tags.emitAll(handler);
    Mockito.verify(handler).handle("bar", 42L);
    Mockito.verify(handler).handle("baz", null);
    Mockito.verify(handler).handle("foo", true);
  }

  @Test
  public void testEmitAllRepeated() {
    Tags tags = Tags.builder().addTag("foo", true).addTag("foo", 42).addTag("foo", "bar").build();
    KeyValueHandler handler = Mockito.mock(KeyValueHandler.class);
    tags.emitAll(handler);
    Mockito.verify(handler).handle("foo", 42L);
    Mockito.verify(handler).handle("foo", "bar");
    Mockito.verify(handler).handle("foo", true);
  }

  @Test
  public void testToString() {
    assertToString(Tags.builder(), "");
    assertToString(Tags.builder().addTag("foo").addTag("bar"), "[ bar=true foo=true ]");
    assertToString(Tags.builder().addTag("foo", "value"), "[ foo=\"value\" ]");
    assertToString(Tags.builder().addTag("foo", ""), "[ foo=\"\" ]");
    assertToString(
        Tags.builder().addTag("foo", "escaped\\\"value\""), "[ foo=\"escaped\\\\\\\"value\\\"\" ]");
    assertToString(Tags.builder().addTag("foo", true).addTag("bar", 42), "[ bar=42 foo=true ]");
    // Mixed types will be rare but should be sorted stably in the same order as the tag type enum:
    // boolean < string < integer < double
    assertToString(Tags.builder()
        .addTag("foo", "bar")
        .addTag("foo", true)
        .addTag("foo", 12.3)
        .addTag("foo", 42),
        "[ foo=true foo=\"bar\" foo=42 foo=12.3 ]");
  }

  private static void assertToString(Tags.Builder builder, String expected) {
    assertThat(builder.toString()).isEqualTo(expected);
    assertThat(builder.build().toString()).isEqualTo(expected);
  }
}
