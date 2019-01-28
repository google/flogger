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

package com.google.common.flogger.backend.slf4j;

import static org.mockito.Mockito.*;
import static org.junit.Assert.fail;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.parser.ParseException;
import com.google.common.flogger.testing.FakeLogData;
import com.google.common.flogger.LogContext;
import org.slf4j.Logger;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.mockito.*;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;

@RunWith(JUnit4.class)
public class Slf4jBackendLoggerTest {

  private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
  private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);

  private static LoggerBackend newBackend(Logger logger) {
    return new Slf4jLoggerBackend(logger);
  }

  @Test
  public void testMessage() {
    Logger logger = mock(Logger.class);
    LoggerBackend backend = newBackend(logger);

    backend.log(FakeLogData.of("Hello World"));
    backend.log(FakeLogData.withPrintfStyle("Hello %s %s", "Foo", "Bar"));

    InOrder inOrder = inOrder(logger);
    inOrder.verify(logger).info("Hello World", (Throwable) null);
    inOrder.verify(logger).info("Hello Foo Bar", (Throwable) null);
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testMetadata() {
    Logger logger = mock(Logger.class);
    LoggerBackend backend = newBackend(logger);

    backend.log(
        FakeLogData.withPrintfStyle("Foo='%s'", "bar")
            .addMetadata(COUNT_KEY, 23)
            .addMetadata(ID_KEY, "test ID"));

    verify(logger).info("Foo='bar' [CONTEXT count=23 id=\"test ID\" ]", (Throwable) null);
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testLevels() {
    Logger logger = mock(Logger.class);
    LoggerBackend backend = newBackend(logger);

    backend.log(FakeLogData.of("finest").setLevel(java.util.logging.Level.FINEST));
    backend.log(FakeLogData.of("finer").setLevel(java.util.logging.Level.FINER));
    backend.log(FakeLogData.of("fine").setLevel(java.util.logging.Level.FINE));
    backend.log(FakeLogData.of("config").setLevel(java.util.logging.Level.CONFIG));
    backend.log(FakeLogData.of("info").setLevel(java.util.logging.Level.INFO));
    backend.log(FakeLogData.of("warning").setLevel(java.util.logging.Level.WARNING));
    backend.log(FakeLogData.of("severe").setLevel(java.util.logging.Level.SEVERE));

    InOrder inOrder = inOrder(logger);
    inOrder.verify(logger).trace("finest", (Throwable) null);
    inOrder.verify(logger).trace("finer", (Throwable) null);
    inOrder.verify(logger).debug("fine", (Throwable) null);
    inOrder.verify(logger).debug("config", (Throwable) null);
    inOrder.verify(logger).info("info", (Throwable) null);
    inOrder.verify(logger).warn("warning", (Throwable) null);
    inOrder.verify(logger).error("severe", (Throwable) null);
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testErrorHandling() {
    Logger logger = mock(Logger.class);
    LoggerBackend backend = newBackend(logger);

    LogData data = FakeLogData.withPrintfStyle("Hello %?X World", "ignored");
    try {
      backend.log(data);
      fail("expected ParseException");
    } catch (ParseException expected) {
      verifyZeroInteractions(logger);
      backend.handleError(expected, data);
      verify(logger).error("LOGGING ERROR: invalid flag: Hello %[?]X World\n"
          + "  original message: Hello %?X World\n"
          + "  original arguments:\n"
          + "    ignored\n"
          + "  level: INFO\n"
          + "  timestamp (nanos): 0\n"
          + "  class: com.google.FakeClass\n"
          + "  method: fakeMethod\n"
          + "  line number: 123", expected);
      verifyNoMoreInteractions(logger);
    }
  }

  @Test
  public void testWithThrown() {
    Logger logger = mock(Logger.class);
    LoggerBackend backend = newBackend(logger);

    Throwable cause = new Throwable("Original Cause");
    backend.log(FakeLogData.of("Hello World").addMetadata(LogContext.Key.LOG_CAUSE, cause));

    verify(logger).info("Hello World", cause);
  }


}
