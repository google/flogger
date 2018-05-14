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

import static com.google.common.flogger.LogFormat.PRINTF_STYLE;
import static com.google.common.truth.Truth.assertThat;
import static org.apache.log4j.Level.DEBUG;
import static org.apache.log4j.Level.ERROR;
import static org.apache.log4j.Level.INFO;
import static org.apache.log4j.Level.TRACE;
import static org.apache.log4j.Level.WARN;
import static org.junit.Assert.fail;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.parser.ParseException;
import com.google.common.flogger.testing.FakeLogData;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Log4jBackendLoggerTest {
  private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
  private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);

  @Rule public TestName name = new TestName();

  private static LoggerBackend newBackend(Logger logger) {
    return new Log4jLoggerBackend(logger);
  }

  @Before
  public void setup() {
    removeCachedLoggers();
  }

  @After
  public void cleanup() {
    removeCachedLoggers();
  }

  @Test
  public void testMessage() {
    AssertingLogger logger = getAssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(FakeLogData.of("Hello World"));
    backend.log(FakeLogData.of(PRINTF_STYLE, "Hello %s %s", "Foo", "Bar"));

    logger.assertLogCount(2);
    logger.assertLogEntry(0, INFO, "Hello World");
    logger.assertLogEntry(1, INFO, "Hello Foo Bar");
  }

  @Test
  public void testMetadata() {
    AssertingLogger logger = getAssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(
        FakeLogData.of(PRINTF_STYLE, "Foo='%s'", "bar")
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID"));

    logger.assertLogCount(1);
    logger.assertLogEntry(0, INFO, "Foo='bar' [CONTEXT count=23 id=\"test ID\" ]");
  }

  @Test
  public void testLevels() {
    AssertingLogger logger = getAssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(FakeLogData.of("finest").setLevel(java.util.logging.Level.FINEST));
    backend.log(FakeLogData.of("finer").setLevel(java.util.logging.Level.FINER));
    backend.log(FakeLogData.of("fine").setLevel(java.util.logging.Level.FINE));
    backend.log(FakeLogData.of("config").setLevel(java.util.logging.Level.CONFIG));
    backend.log(FakeLogData.of("info").setLevel(java.util.logging.Level.INFO));
    backend.log(FakeLogData.of("warning").setLevel(java.util.logging.Level.WARNING));
    backend.log(FakeLogData.of("severe").setLevel(java.util.logging.Level.SEVERE));

    logger.assertLogCount(7);
    logger.assertLogEntry(0, TRACE, "finest");
    logger.assertLogEntry(1, TRACE, "finer");
    logger.assertLogEntry(2, DEBUG, "fine");
    logger.assertLogEntry(3, DEBUG, "config");
    logger.assertLogEntry(4, INFO, "info");
    logger.assertLogEntry(5, WARN, "warning");
    logger.assertLogEntry(6, ERROR, "severe");
  }

  @Test
  public void testErrorHandling() {
    AssertingLogger logger = getAssertingLogger();
    LoggerBackend backend = newBackend(logger);

    LogData data = FakeLogData.of(PRINTF_STYLE, "Hello %?X World", "ignored");
    try {
      backend.log(data);
      fail("expected ParseException");
    } catch (ParseException expected) {
      logger.assertLogCount(0);
      backend.handleError(expected, data);
      logger.assertLogCount(1);
      assertThat(logger.getMessage(0)).contains("lo %[?]X Wo");
    }
  }

  @Test
  public void testWithThrown() {
    AssertingLogger logger = getAssertingLogger();
    LoggerBackend backend = newBackend(logger);

    Throwable cause = new Throwable("Original Cause");
    backend.log(FakeLogData.of("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause));

    logger.assertLogCount(1);
    logger.assertThrown(0, cause);
  }

  private AssertingLogger getAssertingLogger() {
    // Each test requires a unique Logger instance. To ensure this the test method name is included
    // into the logger name. This assumes that JUnit never runs the same test multiple times
    // concurrently.
    // Using "toUpperCase()" below is not strictly correct as regards locale etc, but since tests
    // should not start with an upper case character, we should never get a clash.
    String testMethod = name.getMethodName();
    return AssertingLogger.createOrGet(
        "LoggerFor" + testMethod.substring(0, 1).toUpperCase() + testMethod.substring(1));
  }

  private void removeCachedLoggers() {
    ((Hierarchy) LogManager.getLoggerRepository()).clear();
  }
}
