/*
 * Copyright (C) 2017 The Flogger Authors.
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

import static com.google.common.flogger.backend.FormatOptions.FLAG_UPPER_CASE;
import static com.google.common.flogger.backend.FormatOptions.UNSET;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.LogFormat;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.SimpleMessageFormatter.SimpleLogHandler;
import com.google.common.flogger.testing.FakeLogData;
import java.io.IOException;
import java.util.Formattable;
import java.util.Formatter;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleMessageFormatterTest {

  // TODO: More tests here (lots of SimpleMessageFormatter is tested by SimpleLogRecordTest).
  private static final FormatOptions NO_OPTIONS = FormatOptions.getDefault();
  private static final FormatOptions UPPER_CASE = FormatOptions.of(FLAG_UPPER_CASE, UNSET, UNSET);

  private static final MetadataKey<String> STRING_KEY = MetadataKey.single("string", String.class);
  private static final MetadataKey<Integer> INT_KEY = MetadataKey.single("int", Integer.class);
  private static final MetadataKey<Boolean> BOOL_KEY = MetadataKey.single("bool", Boolean.class);

  @Test
  public void testHexFormat() {
    assertThat(formatHex(0xFDB97531, NO_OPTIONS)).isEqualTo("fdb97531");
    assertThat(formatHex(0xFDB97531, UPPER_CASE)).isEqualTo("FDB97531");
    assertThat(formatHex(0x0123456789ABCDEFL, NO_OPTIONS)).isEqualTo("123456789abcdef");
    assertThat(formatHex(0xFEDCBA9876543210L, UPPER_CASE)).isEqualTo("FEDCBA9876543210");
    assertThat(formatHex(0, UPPER_CASE)).isEqualTo("0");
    assertThat(formatHex((byte) -1, UPPER_CASE)).isEqualTo("FF");
    assertThat(formatHex((short) -1, UPPER_CASE)).isEqualTo("FFFF");
  }

  @Test
  public void testToStringFormatsCorrectly() {
    assertThat(SimpleMessageFormatter.toString("Hello World")).isSameAs("Hello World");
    assertThat(SimpleMessageFormatter.toString(10)).isEqualTo("10");
    assertThat(SimpleMessageFormatter.toString(false)).isEqualTo("false");
    // Not what you'd normally get from Object#toString() ...
    assertThat(SimpleMessageFormatter.toString(new String[] {"Foo", "Bar"}))
        .isEqualTo("[Foo, Bar]");
    assertThat(SimpleMessageFormatter.toString(new int[] {1, 2, 3})).isEqualTo("[1, 2, 3]");
  }

  @Test
  public void testMetadataFormatting() {
    assertThat(logMetadata()).isEmpty();
    assertThat(logMetadata(STRING_KEY, "Foo")).isEqualTo("[CONTEXT string=\"Foo\" ]");
    assertThat(logMetadata(INT_KEY, 42)).isEqualTo("[CONTEXT int=42 ]");
    assertThat(logMetadata(BOOL_KEY, true)).isEqualTo("[CONTEXT bool=true ]");

    // Metadata is simple outputted in the order it was added.
    assertThat(logMetadata(STRING_KEY, "Foo", INT_KEY, 42, BOOL_KEY, true))
        .isEqualTo("[CONTEXT string=\"Foo\" int=42 bool=true ]");

    // Tags get embedded as metadata, but always format (sorted) after any other explicit metadata.
    Tags tags = Tags.builder().addTag("value", 23).addTag("msg", "Hello World").build();
    assertThat(logMetadata(Key.TAGS, tags, STRING_KEY, "Foo"))
        .isEqualTo("[CONTEXT string=\"Foo\" msg=\"Hello World\" value=23 ]");
  }

  @Test
  public void testFormattable() {
    Formattable arg = new Formattable() {
      @Override
      public void formatTo(Formatter formatter, int flags, int width, int precision) {
        try {
          formatter.out()
              .append(String.format("[f=%d,w=%d,p=%d]", flags, width, precision));
        } catch (IOException impossible) { }
      }
    };
    assertThat(log("%s", arg)).isEqualTo("[f=0,w=-1,p=-1]");
    assertThat(log("%100s", arg)).isEqualTo("[f=0,w=100,p=-1]");
    assertThat(log("%.25s", arg)).isEqualTo("[f=0,w=-1,p=25]");
    assertThat(log("%100.25s", arg)).isEqualTo("[f=0,w=100,p=25]");
    assertThat(log("%-100s", arg)).isEqualTo("[f=1,w=100,p=-1]");
    assertThat(log("%S", arg)).isEqualTo("[f=2,w=-1,p=-1]");
    assertThat(log("%#s", arg)).isEqualTo("[f=4,w=-1,p=-1]");
    assertThat(log("%-#32.16S", arg)).isEqualTo("[f=7,w=32,p=16]");
  }

  @Test
  public void testToStringError() {
    Object arg = new Object() {
      @Override
      public String toString() {
        throw new RuntimeException("Badness!!");
      }
    };
    assertThat(log("%s", arg)).contains("java.lang.RuntimeException: Badness!!");
  }

  @Test
  public void testFormattableError() {
    Formattable arg = new Formattable() {
      @Override
      public void formatTo(Formatter formatter, int flags, int width, int precision) {
        try {
          // This should be deleted if an error occurs.
          formatter.out().append("UNEXPECTED");
        } catch (IOException e) { }
        throw new RuntimeException("Badness!!");
      }
    };
    assertThat(log("%s", arg)).contains("java.lang.RuntimeException: Badness!!");
    assertThat(log("%s", arg)).doesNotContain("UNEXPECTED");
  }

  private static String formatHex(Number n, FormatOptions options) {
    StringBuilder out = new StringBuilder();
    SimpleMessageFormatter.appendHex(out, n, options);
    return out.toString();
  }

  private static String log(String message, Object... args) {
    SimpleLogHandler handler = getSimpleLogHandler();
    SimpleMessageFormatter.format(FakeLogData.of(LogFormat.PRINTF_STYLE, message, args), handler);
    return handler.toString();
  }

  private static String logMetadata(Object... args) {
    SimpleLogHandler handler = getSimpleLogHandler();
    FakeLogData data = FakeLogData.of(LogFormat.PRINTF_STYLE, "");
    for (int n = 0; n < args.length / 2; n++) {
      data.addMetadata((MetadataKey<?>) args[2 * n], args[(2 * n) + 1]);
    }
    SimpleMessageFormatter.format(data, handler);
    return handler.toString();
  }

  private static SimpleLogHandler getSimpleLogHandler() {
    return new SimpleLogHandler() {
        private String captured = null;

        @Override
        public void handleFormattedLogMessage(Level lvl, String msg, @Nullable Throwable e) {
          captured = msg;
        }

        @Override
        public String toString() {
          return captured;
        }
      };
  }
}
