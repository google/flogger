/*
 * Copyright (C) 2015 The Flogger Authors.
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

package com.google.common.flogger.backend.system;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.parser.ParseException;
import com.google.common.flogger.testing.FakeLogData;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleLogRecordTest {
  private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
  private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);

  @Test
  public void testLiteral() {
    LogData data = FakeLogData.of("literal message").setLevel(Level.WARNING);

    LogRecord record = SimpleLogRecord.create(data);

    assertThat(record.getLevel()).isEqualTo(Level.WARNING);
    assertThat(record.getMessage()).isEqualTo("literal message");
    assertThat(record.getParameters()).isNull();

    // We'll test these once here as it should be the same for all records.
    assertThat(record.getLoggerName()).isEqualTo(data.getLoggerName());
    assertThat(record.getSourceClassName()).isEqualTo(data.getLogSite().getClassName());
    assertThat(record.getSourceMethodName()).isEqualTo(data.getLogSite().getMethodName());
  }

  @Test
  public void testLiteralNull() {
    LogData data = FakeLogData.of(null).setLevel(Level.WARNING);

    LogRecord record = SimpleLogRecord.create(data);

    assertThat(record.getLevel()).isEqualTo(Level.WARNING);
    assertThat(record.getMessage()).isEqualTo("null");
    assertThat(record.getParameters()).isNull();
  }

  @Test
  public void testWithArguments() {
    LogData data = FakeLogData.withBraceStyle("Answer={0}", 42).setLevel(Level.FINE);

    LogRecord record = SimpleLogRecord.create(data);

    assertThat(record.getLevel()).isEqualTo(Level.FINE);
    assertThat(record.getMessage()).isEqualTo("Answer=42");
    assertThat(record.getParameters()).isNull();
  }

  @Test
  public void testWithPrintfFormatting() {
    LogData data = FakeLogData.withPrintfStyle("Hex=%#08x, Int=%1$d", 0xC0DE);

    LogRecord record = SimpleLogRecord.create(data);

    assertThat(record.getLevel()).isEqualTo(Level.INFO);
    assertThat(record.getMessage()).isEqualTo("Hex=0x00c0de, Int=49374");
    assertThat(record.getParameters()).isNull();
  }

  @Test
  public void testWithThrown() {
    Throwable cause = new Throwable("Goodbye World");
    LogData data =
        FakeLogData.withPrintfStyle("Hello World")
            .addMetadata(LogContext.Key.LOG_CAUSE, cause);

    LogRecord record = SimpleLogRecord.create(data);

    assertThat(record.getThrown()).isSameInstanceAs(cause);
  }

  @Test
  public void testErrorHandling() {
    Throwable cause = new Throwable("Original Cause");
    LogData data =
        FakeLogData.withPrintfStyle("Hello World")
            .addMetadata(LogContext.Key.LOG_CAUSE, cause);

    RuntimeException error = new RuntimeException("Runtime Error");
    LogRecord record = SimpleLogRecord.error(error, data);

    assertThat(record.getLevel()).isEqualTo(Level.WARNING);
    assertThat(record.getThrown()).isEqualTo(error);
    assertThat(record.getMessage()).contains("message: Hello World");
    // This is formatted from the original log data.
    assertThat(record.getMessage()).contains("level: INFO");
    // The original cause is in the metadata of the original log data.
    assertThat(record.getMessage()).contains("Original Cause");
  }

  @Test
  public void testWithArgumentsAndMetadata() {
    LogData data =
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .setTimestampNanos(123456789000L)
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID");

    LogRecord record = SimpleLogRecord.create(data);

    assertThat(record.getLevel()).isEqualTo(Level.INFO);
    assertThat(record.getMessage()).isEqualTo("Foo='bar' [CONTEXT count=23 id=\"test ID\" ]");
    assertThat(record.getParameters()).isNull();
    // Just do this once for sanity checking - it's only for debugging.
    record.setMillis(123456789);
    assertThat(record.toString())
        .isEqualTo(
            "SimpleLogRecord {\n"
                + "  message: Foo='bar' [CONTEXT count=23 id=\"test ID\" ]\n"
                + "  arguments: <none>\n"
                + "  original message: Foo='%s'\n"
                + "  original arguments:\n"
                + "    bar\n"
                + "  metadata:\n"
                + "    count: 23\n"
                + "    id: test ID\n"
                + "  level: INFO\n"
                + "  timestamp (nanos): 123456789000\n"
                + "  class: com.google.FakeClass\n"
                + "  method: fakeMethod\n"
                + "  line number: 123\n"
                + "}");
  }

  @Test
  public void testNullArgs() {
    LogData data = FakeLogData.withPrintfStyle("value=%s", new Object[] { null });
    LogRecord record = SimpleLogRecord.create(data);
    assertThat(record.getMessage()).isEqualTo("value=null");
  }

  @Test
  public void testMissingArgs() {
    LogData data = FakeLogData.withPrintfStyle("foo=%s, bar=%s", "FOO");
    LogRecord record = SimpleLogRecord.create(data);
    assertThat(record.getMessage()).isEqualTo("foo=FOO, bar=[ERROR: MISSING LOG ARGUMENT]");
  }

  @Test
  public void testUnusedArgs() {
    LogData data =
        FakeLogData.withPrintfStyle("%2$s %s %<s %s", "a", "b", "c", "d"); // "b a a b"
    LogRecord record = SimpleLogRecord.create(data);
    assertThat(record.getMessage()).isEqualTo("b a a b [ERROR: UNUSED LOG ARGUMENTS]");
  }

  @Test
  public void testTrailingArgsAbove32AreCaught() {
    // 33 arguments with the 33rd argument unreferenced.
    LogData data =
        FakeLogData.withPrintfStyle(
            "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s "
                + "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s",
            new Object[33]);
    LogRecord record = SimpleLogRecord.create(data);
    assertThat(record.getMessage()).contains("[ERROR: UNUSED LOG ARGUMENTS]");
  }

  @Test
  public void testGapsInArgsUpTo32AreCaught() {
    // 33 arguments with the 32rd argument unreferenced.
    LogData data =
        FakeLogData.withPrintfStyle(
            "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s "
                + "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %33$s",
            new Object[33]);
    try {
      SimpleLogRecord.create(data);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("unreferenced arguments [first missing index=31]");
    }
  }

  @Test
  public void testGapsInArgsAbove32AreIgnored() {
    // 34 arguments with the 33rd argument unreferenced (leaving a gap).
    Object[] args = new Object[34];
    args[33] = "UNUSED";
    LogData data =
        FakeLogData.withPrintfStyle(
            "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s "
                + "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %34$s",
            new Object[34]);
    LogRecord record = SimpleLogRecord.create(data);
    assertThat(record.getMessage()).doesNotContain("UNUSED");
  }

  @Test
  public void testToString_b31405251() {
    // Test once with arguments and once without (LogData behaves differently in each case).
    LogData data = FakeLogData.withPrintfStyle("Answer=%d", 42);
    String toString = SimpleLogRecord.create(data).toString();
    // From the SimpleLogRecord point of view, we don't have arguments after formatting.
    assertThat(toString).contains("  message: Answer=42");
    assertThat(toString).contains("  arguments: <none>");
    assertThat(toString).contains("  original message: Answer=%d");

    data = FakeLogData.of("Literal String");
    toString = SimpleLogRecord.create(data).toString();
    assertThat(toString).contains("  message: Literal String");
    assertThat(toString).contains("  arguments: <none>");
    assertThat(toString).contains("  original message: Literal String");
  }
}
