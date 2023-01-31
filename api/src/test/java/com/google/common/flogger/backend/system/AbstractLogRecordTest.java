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

package com.google.common.flogger.backend.system;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LogMessageFormatter;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.common.flogger.backend.SimpleMessageFormatter;
import com.google.common.flogger.testing.FakeLogData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AbstractLogRecordTest {
  private static final LogMessageFormatter DEFAULT_FORMATTER =
      SimpleMessageFormatter.getDefaultFormatter();

  private static final LogMessageFormatter TEST_MESSAGE_FORMATTER =
      new LogMessageFormatter() {
        @Override
        public StringBuilder append(
            LogData logData, MetadataProcessor metadata, StringBuilder out) {
          out.append("Appended: ");
          DEFAULT_FORMATTER.append(logData, metadata, out);
          return out;
        }

        @Override
        public String format(LogData logData, MetadataProcessor metadata) {
          return "Copied: " + DEFAULT_FORMATTER.format(logData, metadata);
        }
      };

  private static final class TestRecord extends AbstractLogRecord {
    TestRecord(String message, Object... args) {
      super(FakeLogData.withPrintfStyle(message, args), Metadata.empty());
    }

    @Override
    protected LogMessageFormatter getLogMessageFormatter() {
      return TEST_MESSAGE_FORMATTER;
    }
  }

  @Test
  public void testGetMessage_caches() {
    AbstractLogRecord record = new TestRecord("Hello %s", "World");
    String message = record.getMessage();
    assertThat(message).isEqualTo("Copied: Hello World");
    assertThat(record.getMessage()).isSameInstanceAs(message);
    assertThat(record.getParameters()).isEmpty();
  }

  @Test
  public void testSetMessage_allowsBraceFormatOverride() {
    AbstractLogRecord record = new TestRecord("Hello %s", "World");
    assertThat(record.getMessage()).isEqualTo("Copied: Hello World");

    // Reset both message and parameters to revert behaviour to a legacy JDK LogRecord.
    record.setMessage("Custom {0}");
    assertThat(record.getMessage()).isEqualTo("Custom {0}");
    assertThat(record.getParameters()).isEmpty();
    // Without parameters, the placeholders are not processed.
    assertThat(record.appendFormattedMessageTo(new StringBuilder()).toString())
        .isEqualTo("Custom {0}");
    assertThat(record.getFormattedMessage()).isEqualTo("Custom {0}");

    record.setParameters(new Object[] {"Parameter"});
    assertThat(record.getParameters()).asList().containsExactly("Parameter");
    assertThat(record.appendFormattedMessageTo(new StringBuilder()).toString())
        .isEqualTo("Custom Parameter");
    assertThat(record.getFormattedMessage()).isEqualTo("Custom Parameter");
  }

  @Test
  public void testAppendFormattedMessageTo_doesNotCacheByDefault() {
    StringBuilder mutable = new StringBuilder("World");
    AbstractLogRecord record = new TestRecord("Hello %s", mutable);

    assertThat(record.appendFormattedMessageTo(new StringBuilder()).toString())
        .isEqualTo("Appended: Hello World");
    // In normal use this should never happen (or at least, if it does it's user error).
    mutable.insert(0, "Mutable ");
    assertThat(record.appendFormattedMessageTo(new StringBuilder()).toString())
        .isEqualTo("Appended: Hello Mutable World");
  }

  @Test
  public void testAppendFormattedMessageTo_cachesAfterGetMessage() {
    StringBuilder mutable = new StringBuilder("World");
    AbstractLogRecord record = new TestRecord("Hello %s", mutable);

    assertThat(record.appendFormattedMessageTo(new StringBuilder()).toString())
        .isEqualTo("Appended: Hello World");
    // Read LogData and format to a String which is cached.
    String unused = record.getMessage();
    mutable.insert(0, "IGNORED");
    assertThat(record.appendFormattedMessageTo(new StringBuilder()).toString())
        .isEqualTo("Copied: Hello World");
  }

  @Test
  public void testGetFormattedMessage_doesCacheByDefault() {
    StringBuilder mutable = new StringBuilder("World");
    AbstractLogRecord record = new TestRecord("Hello %s", mutable);

    String message = record.getFormattedMessage();
    assertThat(message).isEqualTo("Copied: Hello World");
    assertThat(record.getFormattedMessage()).isSameInstanceAs(message);
  }
}
