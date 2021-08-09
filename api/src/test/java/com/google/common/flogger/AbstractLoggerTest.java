/*
 * Copyright (C) 2021 The Flogger Authors.
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

package com.google.common.flogger;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.LoggingException;
import com.google.common.flogger.testing.TestLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/* See LogContextTest.java for the vast majority of tests related to base logging behaviour. */
@RunWith(JUnit4.class)
public final class AbstractLoggerTest {
  // Matches ISO 8601 date/time format.
  // See: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
  private static final Pattern ISO_TIMESTAMP_PREFIX =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}\\:\\d{2}\\.\\d{3}[-+]\\d{4}: .*");

  private static class TestBackend extends LoggerBackend {
    final List<String> logged = new ArrayList<>();

    @Override
    public String getLoggerName() {
      return "<unused>";
    }

    @Override
    public boolean isLoggable(Level lvl) {
      return true;
    }

    // Format without using Flogger util classes, so we can test what happens if arguments cause
    // errors (the core utility classes handle this properly).
    @Override
    public void log(LogData data) {
      if (data.getTemplateContext() != null) {
        logged.add(
            String.format(
                Locale.ROOT, data.getTemplateContext().getMessage(), data.getArguments()));
      } else {
        logged.add(data.getLiteralArgument().toString());
      }
    }

    // Don't handle any errors in the backend so we can test "last resort" error handling.
    @Override
    public void handleError(RuntimeException error, LogData badData) {
      throw error;
    }
  }

  // Needed for testing error handling since you can't catch raw "Error" in tests.
  private static class MyError extends Error {
    MyError(String message) {
      super(message);
    }
  }

  private final ByteArrayOutputStream err = new ByteArrayOutputStream();
  private PrintStream systemErr;

  @Before
  public void redirectStderr() throws UnsupportedEncodingException {
    systemErr = System.err;
    System.setErr(new PrintStream(err, true, UTF_8.name()));
  }

  @After
  public void restoreStderr() {
    System.setErr(systemErr);
  }

  @Test
  public void testErrorReporting() {
    TestBackend backend = new TestBackend();
    TestLogger logger = TestLogger.create(backend);
    Object bad =
        new Object() {
          @Override
          public String toString() {
            throw new RuntimeException("Ooopsie");
          }
        };

    logger.atInfo().log("evil value: %s", bad);
    assertThat(backend.logged).isEmpty();
    List<String> stdErrLines = Splitter.on('\n').splitToList(stdErrString());
    assertThat(stdErrLines).isNotEmpty();
    String errLine = stdErrLines.get(0);
    assertThat(errLine).matches(ISO_TIMESTAMP_PREFIX);
    assertThat(errLine).contains("logging error");
    assertThat(errLine).contains("com.google.common.flogger.AbstractLoggerTest.testErrorReporting");
    assertThat(errLine).contains("java.lang.RuntimeException: Ooopsie");
  }

  @Test
  public void testBadError() {
    TestBackend backend = new TestBackend();
    TestLogger logger = TestLogger.create(backend);
    // A worst case scenario whereby an object's toString() throws an exception which itself throws
    // an exception. If we can handle this, we can handle just about anything!
    Object evil =
        new Object() {
          @Override
          public String toString() {
            throw new RuntimeException("Ooopsie") {
              @Override
              public String toString() {
                throw new RuntimeException("<<IGNORED>>");
              }
            };
          }
        };

    logger.atInfo().log("evil value: %s", evil);
    assertThat(backend.logged).isEmpty();
    List<String> stdErrLines = Splitter.on('\n').splitToList(stdErrString());
    assertThat(stdErrLines).isNotEmpty();
    String errLine = stdErrLines.get(0);
    assertThat(errLine).matches(ISO_TIMESTAMP_PREFIX);
    assertThat(errLine).contains("logging error");
    // It's in a subclass of RuntimeException in this case, so only check the message.
    assertThat(errLine).contains("Ooopsie");
    // We didn't handle the inner exception, but that's fine.
    stdErrLines.forEach(line -> assertThat(line).doesNotContain("<<IGNORED>>"));
  }

  @Test
  public void testRecurionHandling() {
    TestBackend backend = new TestBackend();
    // The test logger does not handle errors gracefully, which should trigger the fallback error
    // handling in AbstractLogger (which is what we want to test).
    TestLogger logger = TestLogger.create(backend);
    Object bad =
        new Object() {
          @Override
          public String toString() {
            logger.atInfo().log("recursion: %s", this);
            return "<unused>";
          }
        };

    logger.atInfo().log("evil value: %s", bad);
    // Matches AbstractLogger#MAX_ALLOWED_RECURSION_DEPTH.
    assertThat(backend.logged).hasSize(100);
    List<String> stdErrLines = Splitter.on('\n').splitToList(stdErrString());
    assertThat(stdErrLines).isNotEmpty();
    String errLine = stdErrLines.get(0);
    assertThat(errLine).matches(ISO_TIMESTAMP_PREFIX);
    assertThat(errLine).contains("logging error");
    assertThat(errLine).contains("unbounded recursion in log statement");
    assertThat(errLine).contains("com.google.common.flogger.AbstractLoggerTest");
  }

  @Test
  public void testLoggingExceptionAllowed() {
    // A backend which deliberately triggers an internal error.
    TestBackend backend =
        new TestBackend() {
          @Override
          public void log(LogData data) {
            throw new LoggingException("Allowed");
          }
        };
    TestLogger logger = TestLogger.create(backend);

    try {
      logger.atInfo().log("doomed to fail");
      Assert.fail("expected LoggingException");
    } catch (LoggingException expected) {
      // pass
    }
    assertThat(backend.logged).isEmpty();
    assertThat(stdErrString()).isEmpty();
  }

  @Test
  public void testLoggingErrorAllowed() {
    // A backend which triggers an Error of some kind.
    TestBackend backend =
        new TestBackend() {
          @Override
          public void log(LogData data) {
            throw new MyError("Allowed");
          }
        };
    TestLogger logger = TestLogger.create(backend);

    try {
      logger.atInfo().log("doomed to fail");
      Assert.fail("expected MyError");
    } catch (MyError expected) {
      // pass
    }
    assertThat(backend.logged).isEmpty();
    assertThat(stdErrString()).isEmpty();
  }

  private String stdErrString() {
    try {
      return err.toString(UTF_8.name());
    } catch (UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
