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

package com.google.common.flogger.backend;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KeyValueFormatterTest {

  @Test public void testNoValues() {
    StringBuilder out = new StringBuilder();
    formatter(out).done();
    assertThat(out.toString()).isEmpty();

    out = new StringBuilder("Hello World");
    formatter(out).done();
    assertThat(out.toString()).isEqualTo("Hello World");
  }

  @Test public void testPrefixSeparator() {
    // 3 cases: nothing, space or newline
    StringBuilder out = new StringBuilder();
    KeyValueFormatter kvf = formatter(out);
    kvf.handle("foo", 23);
    kvf.done();
    assertThat(out.toString()).isEqualTo("<< foo=23 >>");

    out = new StringBuilder("Message");
    kvf = formatter(out);
    kvf.handle("foo", 23);
    kvf.done();
    assertThat(out.toString()).isEqualTo("Message << foo=23 >>");

    out = new StringBuilder("Multi\nLine");
    kvf = formatter(out);
    kvf.handle("foo", 23);
    kvf.done();
    assertThat(out.toString()).isEqualTo("Multi\nLine\n<< foo=23 >>");
  }

  @Test
  public void testMultipleValues() {
    StringBuilder out = new StringBuilder("Message");
    KeyValueFormatter kvf = formatter(out);
    kvf.handle("foo", 23);
    kvf.handle("bar", false);
    kvf.done();
    assertThat(out.toString()).isEqualTo("Message << foo=23 bar=false >>");
  }

  private enum Foo { BAR };

  @Test
  public void testTypeQuoting() {
    // Safe types are not quoted.
    assertThat(format("x", 23)).isEqualTo("x=23");
    assertThat(format("x", 23L)).isEqualTo("x=23");
    assertThat(format("x", 1.23)).isEqualTo("x=1.23");
    assertThat(format("x", 1.23D)).isEqualTo("x=1.23");
    assertThat(format("x", 1.00D)).isEqualTo("x=1.0");
    assertThat(format("x", true)).isEqualTo("x=true");

    // It's not 100% clear what's the best thing to do with a null value. It can exist, because of
    // tags and custom keys. For now treat it as a positive boolean (ie. this tag exists).
    assertThat(format("x", null)).isEqualTo("x=true");

    // Enums are currently quoted, but wouldn't need to be if the name() rather than the toString()
    // was used to generate to value.
    assertThat(format("x", Foo.BAR)).isEqualTo("x=\"BAR\"");

    // Strings, characters and unknown types are quoted.
    assertThat(format("x", "tag")).isEqualTo("x=\"tag\"");
    assertThat(format("x", 'y')).isEqualTo("x=\"y\"");
    assertThat(format("x", new StringBuilder("foo"))).isEqualTo("x=\"foo\"");
    // In general, the toString() is used.
    Object foo = new Object() {
      @Override
      public String toString() {
        return "unsafe";
      }
    };
    assertThat(format("x", foo)).isEqualTo("x=\"unsafe\"");
  }

  @Test
  public void testEscaping() {
    assertThat(format("x", "Double \"Quotes\"")).isEqualTo("x=\"Double \\\"Quotes\\\"\"");
    assertThat(format("x", "\\Backslash\\")).isEqualTo("x=\"\\\\Backslash\\\\\"");
    assertThat(format("x", "New\nLine")).isEqualTo("x=\"New\\nLine\"");
    assertThat(format("x", "Carriage\rReturn")).isEqualTo("x=\"Carriage\\rReturn\"");
    assertThat(format("x", "\tTab")).isEqualTo("x=\"\\tTab\"");
    assertThat(format("x", "Unsafe\0Chars")).isEqualTo("x=\"Unsafe�Chars\"");

    // Surrogate pairs are preserved rather than being escaped.
    assertThat(format("x", "\uD83D\uDE00")).isEqualTo("x=\"\uD83D\uDE00\"");  // 😀
  }

  private static KeyValueFormatter formatter(StringBuilder out) {
    return new KeyValueFormatter("<< ", " >>", out);
  }

  private static String format(String key, Object value) {
    StringBuilder out = new StringBuilder();
    KeyValueFormatter kvf = new KeyValueFormatter("", "", out);
    kvf.handle(key, value);
    kvf.done();
    return out.toString();
  }
}
