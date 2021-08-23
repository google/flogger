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

package com.google.common.flogger.backend.log4j2;

import static com.google.common.truth.Truth.assertThat;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.ERROR;
import static org.apache.logging.log4j.Level.INFO;
import static org.apache.logging.log4j.Level.TRACE;
import static org.apache.logging.log4j.Level.WARN;
import static org.junit.Assert.fail;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.parser.ParseException;
import com.google.common.flogger.testing.FakeLogData;
import com.google.common.flogger.testing.FakeLogSite;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Log4j2Test {
  // -------- Constants for tests --------

  private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
  private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);

  // -------- Test setup shenanigans --------

  // This test code is all rather painful at the moment. It's done like this rather than using the
  // more "normal" approaches for testing log4j2 because:
  // a) it means not needing the additional log4j2 test JAR dependency.
  // b) it's testing at a more structured level.

  private static final AtomicInteger uid = new AtomicInteger();

  private static final class CapturingAppender extends AbstractAppender {
    static final String NAME = "Capturing Appender";
    private final List<LogEvent> events = new ArrayList<>();

    CapturingAppender() {
      super(NAME, null, PatternLayout.createDefaultLayout(), true, null);
      start();
    }

    @Override
    public void append(LogEvent event) {
      events.add(event);
    }
  }

  private Logger logger;
  private CapturingAppender appender;
  private LoggerBackend backend;
  private List<LogEvent> events;

  @Before
  public void setUpLoggerBackend() {
    // need to reset logger config to prevent a clash with log4j2scopedloggingtest
    final LoggerContext context = LoggerContext.getContext(false);
    context.setConfiguration(new DefaultConfiguration());
    context.updateLoggers();

    // A unique name should produce a different logger for each test allowing tests to be run in
    // parallel.
    String loggerName = String.format("%s_%02d", Log4j2Test.class.getName(), uid.incrementAndGet());
    logger = (Logger) LogManager.getLogger(loggerName);
    appender = new CapturingAppender();
    logger.addAppender(appender);
    logger.setLevel(TRACE);
    backend = new Log4j2LoggerBackend(logger);
    events = appender.events;
  }

  @After
  public void tearDown() {
    logger.removeAppender(appender);
    appender.stop();
  }

  // -------- Test helper methods --------

  String getMessage(int index) {
    return events.get(index).getMessage().getFormattedMessage();
  }

  void assertLogCount(int count) {
    assertThat(events).hasSize(count);
  }

  void assertLogEntry(int index, Level level, String message) {
    LogEvent event = events.get(index);
    assertThat(event.getLoggerName()).isEqualTo(logger.getName());
    assertThat(event.getLevel()).isEqualTo(level);
    assertThat(event.getMessage().getFormattedMessage()).isEqualTo(message);
    assertThat(event.getThrown()).isNull();
  }

  void assertLogSite(int index, String className, String methodName, int line, String file) {
    LogEvent event = events.get(index);
    StackTraceElement source = event.getSource();
    assertThat(source.getClassName()).isEqualTo(className);
    assertThat(source.getMethodName()).isEqualTo(methodName);
    assertThat(source.getFileName()).isEqualTo(file);
    assertThat(source.getLineNumber()).isEqualTo(line);
  }

  void assertThrown(int index, Throwable thrown) {
    assertThat(events.get(index).getThrown()).isSameInstanceAs(thrown);
  }

  // -------- Unit tests start here (largely copied from the log4j tests) --------

  @Test
  public void testSimple() throws Exception {
    backend.log(FakeLogData.of("Hello World"));
    assertThat(getMessage(0)).isEqualTo("Hello World");
    assertLogEntry(0, INFO, "Hello World");
  }

  @Test
  public void testMessage() {
    backend.log(FakeLogData.of("Hello World"));
    backend.log(FakeLogData.withPrintfStyle("Hello %s %s", "Foo", "Bar"));

    assertLogCount(2);
    assertLogEntry(0, INFO, "Hello World");
    assertLogEntry(1, INFO, "Hello Foo Bar");
  }

  @Test
  public void testMetadata() {
    backend.log(
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID"));

    assertLogCount(1);
    assertLogEntry(0, INFO, "Foo='bar' [CONTEXT count=23 id=\"test ID\" ]");
  }

  @Test
  public void testLevels() {
    backend.log(FakeLogData.of("finest").setLevel(java.util.logging.Level.FINEST));
    backend.log(FakeLogData.of("finer").setLevel(java.util.logging.Level.FINER));
    backend.log(FakeLogData.of("fine").setLevel(java.util.logging.Level.FINE));
    backend.log(FakeLogData.of("config").setLevel(java.util.logging.Level.CONFIG));
    backend.log(FakeLogData.of("info").setLevel(java.util.logging.Level.INFO));
    backend.log(FakeLogData.of("warning").setLevel(java.util.logging.Level.WARNING));
    backend.log(FakeLogData.of("severe").setLevel(java.util.logging.Level.SEVERE));

    assertLogCount(7);
    assertLogEntry(0, TRACE, "finest");
    assertLogEntry(1, TRACE, "finer");
    assertLogEntry(2, DEBUG, "fine");
    assertLogEntry(3, DEBUG, "config");
    assertLogEntry(4, INFO, "info");
    assertLogEntry(5, WARN, "warning");
    assertLogEntry(6, ERROR, "severe");
  }

  @Test
  public void testSource() {
    backend.log(
        FakeLogData.of("First")
            .setLogSite(FakeLogSite.create("<class>", "<method>", 42, "<file>")));
    backend.log(
        FakeLogData.of("No file").setLogSite(FakeLogSite.create("<class>", "<method>", 42, null)));
    backend.log(
        FakeLogData.of("No line").setLogSite(FakeLogSite.create("<class>", "<method>", -1, null)));

    assertLogCount(3);
    assertLogSite(0, "<class>", "<method>", 42, "<file>");
    assertLogSite(1, "<class>", "<method>", 42, null);
    assertLogSite(2, "<class>", "<method>", -1, null);
  }

  @Test
  public void testErrorHandling() {
    LogData data = FakeLogData.withPrintfStyle("Hello %?X World", "ignored");
    try {
      backend.log(data);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertLogCount(0);
      backend.handleError(expected, data);
      assertLogCount(1);
      assertThat(getMessage(0)).contains("lo %[?]X Wo");
    }
  }

  @Test
  public void testWithThrown() {
    Throwable cause = new Throwable("Original Cause");
    backend.log(FakeLogData.of("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause));

    assertLogCount(1);
    assertThrown(0, cause);
  }
}
