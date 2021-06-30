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

import static com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_ALT_FORM;
import static com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_LEADING_ZEROS;
import static com.google.common.flogger.backend.FormatOptions.FLAG_UPPER_CASE;
import static com.google.common.flogger.backend.FormatOptions.UNSET;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.LogSite;
import com.google.common.flogger.testing.FakeLogSite;
import java.util.Formattable;
import java.util.Formatter;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MessageUtilsTest {
  // TODO: More tests here (lots of MessageUtils is tested by SimpleLogRecordTest).
  private static final FormatOptions NO_OPTIONS = FormatOptions.getDefault();
  private static final FormatOptions UPPER_CASE = FormatOptions.of(FLAG_UPPER_CASE, UNSET, UNSET);

  @Test
  public void testSafeToString() {
    assertThat(MessageUtils.safeToString("Hello World")).isSameInstanceAs("Hello World");
    assertThat(MessageUtils.safeToString(10)).isEqualTo("10");
    assertThat(MessageUtils.safeToString(false)).isEqualTo("false");
    // Not what you'd normally get from Object#toString() ...
    assertThat(MessageUtils.safeToString(new String[] {"Foo", "Bar"})).isEqualTo("[Foo, Bar]");
    assertThat(MessageUtils.safeToString(new int[] {1, 2, 3})).isEqualTo("[1, 2, 3]");
    assertThat(MessageUtils.safeToString(null)).isEqualTo("null");
  }

  @Test
  public void testSafeToString_nullToString() {
    Object value =
        new Object() {
          @SuppressWarnings("ToStringReturnsNull") // intentional for testing
          @Override
          public String toString() {
            return null;
          }
        };
    assertThat(MessageUtils.safeToString(value)).contains(value.getClass().getName());
    assertThat(MessageUtils.safeToString(value)).contains("toString() returned null");
  }

  @Test
  public void testSafeToString_toStringError() {
    Object value =
        new Object() {
          @Override
          public String toString() {
            throw new IllegalArgumentException("Badness");
          }
        };
    assertThat(MessageUtils.safeToString(value))
        .contains("java.lang.IllegalArgumentException: Badness");
  }

  @Test
  public void testSafeFormatTo() {
    Formattable arg =
        new Formattable() {
          @Override
          public void formatTo(Formatter formatter, int flags, int width, int precision) {
            formatter.format((Locale) null, "[f=%d,w=%d,p=%d]", flags, width, precision);
          }
        };
    StringBuilder out = new StringBuilder();
    // FormattableFlags.LEFT_JUSTIFY == 1 << 0 = 1
    // FormattableFlags.UPPERCASE == 1 << 1 = 2
    // FormattableFlags.ALTERNATE == 1 << 2 = 4
    MessageUtils.safeFormatTo(arg, out, FormatOptions.of(FLAG_UPPER_CASE, 4, 2));
    assertThat(out.toString()).isEqualTo("[f=2,w=4,p=2]");

    // Not all flags are passed into the callback.
    out.setLength(0);
    MessageUtils.safeFormatTo(
        arg, out, FormatOptions.of(FLAG_SHOW_LEADING_ZEROS + FLAG_SHOW_ALT_FORM, 1, 0));
    assertThat(out.toString()).isEqualTo("[f=4,w=1,p=0]");
  }

  @Test
  public void testSafeFormatTo_error() {
    Formattable badValue =
        new Formattable() {
          @Override
          public void formatTo(Formatter formatter, int flags, int width, int precision) {
            formatter.format((Locale) null, "DISCARDED");
            throw new IllegalArgumentException("Badness");
          }
        };
    StringBuilder out = new StringBuilder();
    MessageUtils.safeFormatTo(badValue, out, FormatOptions.getDefault());
    String message = out.toString();
    assertThat(message).contains("java.lang.IllegalArgumentException: Badness");
    assertThat(message).doesNotContain("DISCARDED");
  }

  @Test
  public void testAppendLogSite() {
    StringBuilder out = new StringBuilder();
    LogSite logSite = FakeLogSite.create("<class>", "<method>", 32, "Ignored.java");
    assertThat(MessageUtils.appendLogSite(logSite, out)).isTrue();
    assertThat(out.toString()).isEqualTo("<class>.<method>:32");

    out.setLength(0);
    assertThat(MessageUtils.appendLogSite(LogSite.INVALID, out)).isFalse();
    assertThat(out.toString()).isEmpty();
  }

  @Test
  public void testAppendHex() {
    assertThat(formatHex(0xFDB97531, NO_OPTIONS)).isEqualTo("fdb97531");
    assertThat(formatHex(0xFDB97531, UPPER_CASE)).isEqualTo("FDB97531");
    assertThat(formatHex(0x0123456789ABCDEFL, NO_OPTIONS)).isEqualTo("123456789abcdef");
    assertThat(formatHex(0xFEDCBA9876543210L, UPPER_CASE)).isEqualTo("FEDCBA9876543210");
    assertThat(formatHex(0, UPPER_CASE)).isEqualTo("0");
    assertThat(formatHex((byte) -1, UPPER_CASE)).isEqualTo("FF");
    assertThat(formatHex((short) -1, UPPER_CASE)).isEqualTo("FFFF");
  }

  private static String formatHex(Number n, FormatOptions options) {
    StringBuilder out = new StringBuilder();
    MessageUtils.appendHex(out, n, options);
    return out.toString();
  }
}
