/*
 * Copyright (C) 2013 The Flogger Authors.
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

import static com.google.common.flogger.LazyArgs.lazy;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.common.base.Supplier;
import com.google.common.flogger.backend.KeyValueHandler;
import com.google.common.truth.StringSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the Google specific extensions to Fluent Logger.
 */
// TODO(user): Make this use a fake/test backend rather than relying on the system one.
@RunWith(JUnit4.class)
public class GoogleLoggerTest {
  // Metadata keys (single keys will be the most common).
  private static final MetadataKey<String> ID = MetadataKey.single("id", String.class);
  private static final MetadataKey<Integer> FLAG = MetadataKey.repeated("flags", Integer.class);

  // A simple composite value for testing custom metadata keys.
  private static final class Point {
    final int x;
    final int y;

    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  // An example of a metadata key subclass which can decompose a composite value (perhaps to avoid
  // expensive or unwanted processing in the toString() method).
  private static final MetadataKey<Point> POINT =
      new MetadataKey<Point>("point", Point.class, false) {
    @Override
    public void emit(Object value, KeyValueHandler out) {
      Point p = cast(value);
      out.handle("point.x", p.x);
      out.handle("point.y", p.y);
    }
  };

  private GoogleLogger logger;
  private AssertingHandler assertingHandler;

  @Before
  public void addAssertingHandler() {
    logger = GoogleLogger.forEnclosingClass();
    assertingHandler = new AssertingHandler();
    assertingHandler.setLevel(Level.INFO);
    Logger jdkLogger = Logger.getLogger(GoogleLoggerTest.class.getName());
    jdkLogger.setUseParentHandlers(false);
    jdkLogger.addHandler(assertingHandler);
    jdkLogger.setLevel(Level.INFO);
  }

  @After
  public void verifyAndRemoveAssertingHandler() {
    Logger.getLogger(GoogleLoggerTest.class.getName()).removeHandler(assertingHandler);
  }

  @Test
  public void testSimpleLogging() {
    logger.atInfo().log("Hello World");
    assertingHandler.assertOnlyLog().contains("Hello World");
  }

  @Test
  public void testArrayLogging() {
    logger.atInfo().log("Hello %s World", new Object[] {"foo", "bar"});
    assertingHandler.assertOnlyLog().contains("Hello [foo, bar] World");
  }

  // The LazyArgs mechanism is also tested in the core "api" package, but those tests cannot use
  // lamdbas or method references. This test is really only here since the "google" package can
  // test the lazy() method in the way it's intended to be used.
  @Test
  public void testLazyLogging() {
    // A supplier that can only be called once (to show that lazy evaluation is actually lazy).
    Supplier<String> expensive = new Supplier<String>() {
      private boolean wasEvaluated = false;

      @Override
      public String get() {
        assertThat(wasEvaluated).isFalse();
        wasEvaluated = true;
        return "Expensive";
      }
    };

    // This log statement is hit 5 times but "expensive" is only evaluated once (when it's logged).
    for (int n = 0; n < 5; n++) {
      logger.atInfo().every(10).log("Hello %s %s", lazy(expensive::get), "World");
    }
    assertingHandler.assertOnlyLog().contains("Hello Expensive World");
  }

  @Test
  public void testWithMethod() {
    // Adding a key/value pair.
    logger.atInfo().with(ID, "bar").log("With metadata");
    assertingHandler.assertOnlyLog().containsMatch("With metadata.*\\[CONTEXT.*id=\"bar\".*\\]");

    // Null values are a no-op
    logger.atInfo().with(ID, null).log("With metadata");
    assertingHandler.assertOnlyLog().doesNotContain("id");

    // But null keys throw.
    assertThrows(NullPointerException.class, () -> logger.atInfo().with(null, "").log("Nope!"));
    assertThrows(NullPointerException.class, () -> logger.atInfo().with(null, null).log("Nope!"));

    // And the no-op implementation also throws (rather than not logging anything).
    logger.atFinest().log("Should be disabled for test");
    assertingHandler.assertNoLogs();
    assertThrows(NullPointerException.class, () -> logger.atFinest().with(null, "").log("Nope!"));
    assertThrows(NullPointerException.class, () -> logger.atFinest().with(null, null).log("Nope!"));
  }

  @Test
  public void testWithMethod_repeated() {
    // Not-repeatable keys have "last one wins" semantics.
    logger.atInfo().with(ID, "bar").with(ID, "baz").log("Last one wins");
    assertingHandler.assertOnlyLog().doesNotContain("bar");

    logger.atInfo().with(ID, "bar").with(ID, "baz").log("Last one wins");
    assertingHandler.assertOnlyLog().contains("id=\"baz\"");

    // Repeated keys preserve the order the with() methods are called in the log statement.
    logger.atInfo().with(FLAG, 1).with(FLAG, 2).log("Allow both");
    assertingHandler.assertOnlyLog().contains("flags=1 flags=2");
  }

  @Test
  public void testWithMethod_custom() {
    logger
        .atInfo()
        .with(POINT, new Point(17, 29))
        .log("¯\\_(ツ)_//¯");
    assertingHandler.assertOnlyLog().contains("point.x=17 point.y=29");
  }

  @Test
  public void testWithMethod_mixed() {
    // Output order is the same as statement order.
    logger
        .atInfo()
        .with(FLAG, 42)
        .with(POINT, new Point(17, 29))
        .with(FLAG, 23)
        .log("¯\\_(ツ)_//¯");
    assertingHandler.assertOnlyLog().contains("flags=42 point.x=17 point.y=29 flags=23");
  }

  @Test
  public void testLoggerConfig() {
    LoggerConfig.of(logger).setLevel(Level.WARNING);
    logger.atInfo().log("Hello First World");
    LoggerConfig.of(logger).setLevel(Level.INFO);
    logger.atInfo().log("Hello Second World");
    assertingHandler.assertOnlyLog().contains("Hello Second World");
  }

  // Ensure that forEnclosingClass() creates a logger with the expected name, either by
  // stack analysis or compile-time injection.
  @Test
  public void testEnclosingClassName() {
    assertThat(logger.getBackend().getLoggerName())
        .isEqualTo("com.google.common.flogger.GoogleLoggerTest");
    assertThat(Nested.logger.getBackend().getLoggerName())
        .isEqualTo("com.google.common.flogger.GoogleLoggerTest.Nested");
  }

  private static class Nested {
    static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  }

  @Test
  public void testInjectedClassName() {
    @SuppressWarnings("deprecation")
    GoogleLogger logger = GoogleLogger.forInjectedClassName("foo/bar/Baz");
    assertThat(logger.getBackend().getLoggerName()).isEqualTo("foo.bar.Baz");
  }

  @Test
  public void testInjectedInnerClassName() {
    @SuppressWarnings("deprecation")
    GoogleLogger logger = GoogleLogger.forInjectedClassName("java/util/Map$Entry");
    assertThat(logger.getBackend().getLoggerName()).isEqualTo("java.util.Map.Entry");
  }

  private static class AssertingHandler extends Handler {
    private List<LogRecord> logRecords = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      if (isLoggable(record)) {
        logRecords.add(record);
      }
    }

    StringSubject assertOnlyLog() {
      assertThat(logRecords).hasSize(1);
      LogRecord logRecord = logRecords.get(0);
      flush();
      return assertThat(logRecordToString(logRecord));
    }

    public void assertNoLogs() {
      assertWithMessage("unexpected log recorded").that(logRecords).isEmpty();
    }

    private String logRecordToString(LogRecord logRecord) {
      StringBuilder sb = new StringBuilder();
      String message = new SimpleFormatter().formatMessage(logRecord);
      sb.append(logRecord.getLevel()).append(": ").append(message).append("\n");

      Throwable thrown = logRecord.getThrown();
      if (thrown != null) {
        sb.append(thrown);
      }
      return sb.toString().trim();
    }

    @Override
    public void flush() {
      logRecords.clear();
    }

    @Override
    public void close() {
      logRecords = null;
    }
  }

  // Hack version of the JUnit 4.13 assertThrows() method that's not available everywhere.
  private static void assertThrows(Class<? extends RuntimeException> err, Runnable fn) {
    try {
      fn.run();
      fail("expected exception of type " + err.getName());
    } catch (RuntimeException e) {
      assertThat(e).isInstanceOf(err);
    }
  }
}
