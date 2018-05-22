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

package com.google.common.flogger.backend.log4j;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.SimpleMessageFormatter.SimpleLogHandler;
import com.google.common.flogger.testing.FakeLogData;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogDataFormatterTest {
  private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
  private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);

  @Test
  public void testFormatBadLogData() {
    Throwable cause = new Throwable("Original Cause");
    LogData data =
        FakeLogData.withPrintfStyle("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause);

    SimpleLogHandler handler = getSimpleLogHandler();
    RuntimeException error = new RuntimeException("Runtime Error");
    LogDataFormatter.formatBadLogData(error, data, handler);

    String message = handler.toString();
    assertThat(message).contains("message: Hello World");
    assertThat(message).contains("level: INFO");
    assertThat(message).contains("Original Cause");
  }

  @Test
  public void testAppendLogData() {
    LogData data =
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .setTimestampNanos(123456789000L)
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID");

    StringBuilder b = new StringBuilder();
    LogDataFormatter.appendLogData(data, b);
    assertThat(b.toString())
        .isEqualTo(
            "  original message: Foo='%s'\n"
                + "  original arguments:\n"
                + "    bar\n"
                + "  metadata:\n"
                + "    count: 23\n"
                + "    id: test ID\n"
                + "  level: INFO\n"
                + "  timestamp (nanos): 123456789000\n"
                + "  class: com.google.FakeClass\n"
                + "  method: fakeMethod\n"
                + "  line number: 123");
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
