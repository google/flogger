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
import static org.apache.log4j.Level.DEBUG;
import static org.apache.log4j.Level.ERROR;
import static org.apache.log4j.Level.INFO;
import static org.apache.log4j.Level.TRACE;
import static org.apache.log4j.Level.WARN;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.testing.FakeLogData;
import java.util.logging.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Log4jLoggingEventUtilTest {
  private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
  private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);

  private static Logger getLogger(LogData logData) {
    return Logger.getLogger(logData.getLoggerName());
  }

  private static LoggingEvent toLog4jLoggingEvent(LogData data) {
    return Log4jLoggingEventUtil.toLog4jLoggingEvent(getLogger(data), data);
  }

  @Test
  public void testLoggerName() {
    LogData data = FakeLogData.of("foo");
    assertThat(toLog4jLoggingEvent(data).getLoggerName()).isEqualTo(data.getLoggerName());
  }

  @Test
  public void testLocationInfo() {
    LogData data = FakeLogData.of("foo");
    LoggingEvent loggingEvent = toLog4jLoggingEvent(data);

    LocationInfo locationInfo = loggingEvent.getLocationInformation();
    assertThat(locationInfo.getClassName()).isEqualTo(data.getLogSite().getClassName());
    assertThat(locationInfo.getMethodName()).isEqualTo(data.getLogSite().getMethodName());
    assertThat(locationInfo.getFileName()).isEqualTo(data.getLogSite().getFileName());
    assertThat(locationInfo.getLineNumber())
        .isEqualTo(Integer.toString(data.getLogSite().getLineNumber()));
  }

  @Test
  public void testLevels() {
    testLevel(Level.FINEST, TRACE);
    testLevel(Level.FINER, TRACE);
    testLevel(Level.FINE, DEBUG);
    testLevel(Level.CONFIG, DEBUG);
    testLevel(Level.INFO, INFO);
    testLevel(Level.WARNING, WARN);
    testLevel(Level.SEVERE, ERROR);
  }

  private void testLevel(Level level, org.apache.log4j.Level expectedLevel) {
    LoggingEvent loggingEvent =
        toLog4jLoggingEvent(FakeLogData.of(level.getName()).setLevel(level));
    assertThat(loggingEvent.getLevel()).isEqualTo(expectedLevel);
    assertThat(loggingEvent.getMessage()).isEqualTo(level.getName());
  }

  @Test
  public void testMessage() {
    LoggingEvent loggingEvent = toLog4jLoggingEvent(FakeLogData.of("Hello World"));
    assertThat(loggingEvent.getMessage()).isEqualTo("Hello World");

    loggingEvent = toLog4jLoggingEvent(FakeLogData.withPrintfStyle("Hello %s %s", "Foo", "Bar"));
    assertThat(loggingEvent.getMessage()).isEqualTo("Hello Foo Bar");
  }

  @Test
  public void testWithThrown() {
    Throwable cause = new Throwable("Goodbye World");
    LogData data =
        FakeLogData.withPrintfStyle("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause);
    assertThat(toLog4jLoggingEvent(data).getThrowableInformation().getThrowable())
        .isSameInstanceAs(cause);
  }

  @Test
  public void testTimestamp() {
    LogData data = FakeLogData.withPrintfStyle("Foo='%s'", "bar").setTimestampNanos(123456000000L);
    assertThat(toLog4jLoggingEvent(data).getTimeStamp()).isEqualTo(123456L);
  }

  @Test
  public void testMetadata() {
    LogData data =
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID");

    assertThat(toLog4jLoggingEvent(data).getMessage())
        .isEqualTo("Foo='bar' [CONTEXT count=23 id=\"test ID\" ]");
  }

  @Test
  public void testErrorHandling() {
    Throwable cause = new Throwable("Original Cause");
    LogData data =
        FakeLogData.withPrintfStyle("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause);

    RuntimeException error = new RuntimeException("Runtime Error");
    LoggingEvent loggingEvent =
        Log4jLoggingEventUtil.toLog4jLoggingEvent(getLogger(data), error, data);

    assertThat(loggingEvent.getLevel()).isEqualTo(WARN);
    assertThat(loggingEvent.getThrowableInformation().getThrowable()).isEqualTo(error);

    String message = (String) loggingEvent.getMessage();
    assertThat(message).contains("message: Hello World");
    // This is formatted from the original log data.
    assertThat(message).contains("level: INFO");
    // The original cause is in the metadata of the original log data.
    assertThat(message).contains("Original Cause");
  }

  @Test
  public void testBadLogDataFormatting() {
    LogData data =
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .setTimestampNanos(123456789000L)
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID");

    RuntimeException error = new RuntimeException("Runtime Error");
    String message =
        (String)
            Log4jLoggingEventUtil.toLog4jLoggingEvent(getLogger(data), error, data).getMessage();

    assertThat(message).contains("level: INFO");
    assertThat(message)
        .contains(
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
}
