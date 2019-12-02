/*
 * Copyright (C) 2016 The Flogger Authors.
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

import static com.google.common.flogger.LogSites.logSite;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeLoggerBackend;
import com.google.common.flogger.testing.TestLogger;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogContextTest {
  // Arbitrary constants of overloaded types for testing argument mappings.
  private static final byte BYTE_ARG = Byte.MAX_VALUE;
  private static final short SHORT_ARG = Short.MAX_VALUE;
  private static final int INT_ARG = Integer.MAX_VALUE;
  private static final long LONG_ARG = Long.MAX_VALUE;
  private static final char CHAR_ARG = 'X';
  private static final Object OBJECT_ARG = new Object();

  @Test
  public void testIsEnabled() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    backend.setLevel(INFO);

    assertThat(logger.atFine().isEnabled()).isFalse();
    assertThat(logger.atInfo().isEnabled()).isTrue();
    assertThat(logger.at(Level.WARNING).isEnabled()).isTrue();
  }

  @Test
  public void testLoggingWithCause() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    Throwable cause = new Throwable();
    logger.atInfo().withCause(cause).log("Hello World");
    backend.assertLastLogged().metadata().hasSize(1);
    backend.assertLastLogged().metadata().containsUniqueEntry(Key.LOG_CAUSE, cause);
    backend.assertLastLogged().hasMessage("Hello World");
  }

  @Test
  public void testLazyArgs() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    logger.atInfo().log("Hello %s", LazyArgs.lazy(() -> "World"));

    logger.atFine().log(
        "Hello %s",
        LazyArgs.lazy(
            () -> {
              throw new RuntimeException(
                  "Lazy arguments should not be evaluated in a disabled log statement");
            }));

    // By the time the backend processes a log statement, lazy arguments have been evaluated.
    backend.assertLastLogged().hasMessage("Hello %s");
    backend.assertLastLogged().hasArguments("World");
  }

  @Test
  public void testFormattedMessage() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    logger.at(INFO).log("Formatted %s", "Message");

    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLastLogged().hasMessage("Formatted %s");
    backend.assertLastLogged().hasArguments("Message");

    // Cannot ask for literal argument as none exists.
    try {
      backend.getLogged(0).getLiteralArgument();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testLiteralMessage() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    logger.at(INFO).log("Literal Message");

    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLastLogged().hasMessage("Literal Message");

    // Cannot ask for format arguments as none exist.
    assertThat(backend.getLogged(0).getTemplateContext()).isNull();
    try {
      backend.getLogged(0).getArguments();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testMultipleMetadata() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    Exception cause = new RuntimeException();

    logger.atInfo().withCause(cause).every(42).log("Hello World");

    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLogged(0).metadata().hasSize(2);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_EVERY_N, 42);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_CAUSE, cause);
  }

  @Test
  public void testAtMostEvery() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    // Logging occurs at: +0ms, +2400ms, +4800ms
    // Note it will not occur at 4200ms (which is the first logging attempt after the
    // 2nd multiple of 2 seconds because the timestamp is reset to be (start + 2400ms)
    // and not (start + 2000ms). atMostEvery() does not rate limit over multiple samples.
    long startNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    for (int millis = 0, count = 0; millis <= 5000; millis += 600) {
      long timestampNanos = startNanos + MILLISECONDS.toNanos(millis);
      logger.at(INFO, timestampNanos).atMostEvery(2, SECONDS).log("Count=%d", count++);
    }

    assertThat(backend.getLoggedCount()).isEqualTo(3);
    backend
        .assertLogged(0)
        .metadata()
        .containsUniqueEntry(Key.LOG_AT_MOST_EVERY, LogSiteStats.newRateLimitPeriod(2, SECONDS));
    backend.assertLogged(0).hasArguments(0);
    backend.assertLogged(1).hasArguments(4);
    backend.assertLogged(2).hasArguments(8);
  }

  @Test
  public void testWasForced_level() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    backend.setLevel(Level.WARNING);
    TestLogger logger = TestLogger.create(backend);

    logger.forceAt(INFO).log("LOGGED");

    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLogged(0).hasMessage("LOGGED");
    backend.assertLogged(0).metadata().hasSize(1);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.WAS_FORCED, true);
    backend.assertLogged(0).wasForced();
  }

  @Test
  public void testWasForced_everyN() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);
    LogSite logSite = FakeLogSite.create("com.example.MyClass", "everyN", 123, null);

    // Log statements always get logged the first time.
    logger.atInfo().every(3).withInjectedLogSite(logSite).log("LOGGED 1");
    logger.atInfo().every(3).withInjectedLogSite(logSite).log("NOT LOGGED");
    // Manually create the forced context (there is no "normal" api for this).
    logger.forceAt(INFO).every(3).withInjectedLogSite(logSite).log("LOGGED 2");
    // This shows that the "forced" context does not count towards the rate limit count (otherwise
    // this log statement would have been logged).
    logger.atInfo().every(3).withInjectedLogSite(logSite).log("NOT LOGGED");

    assertThat(backend.getLoggedCount()).isEqualTo(2);
    backend.assertLogged(0).hasMessage("LOGGED 1");
    // No rate limit metadata was added, but it was marked as forced.
    backend.assertLogged(1).hasMessage("LOGGED 2");
    backend.assertLogged(1).metadata().hasSize(1);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.WAS_FORCED, true);
  }

  @Test
  public void testWasForced_atMostEvery() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);
    LogSite logSite = FakeLogSite.create("com.example.MyClass", "atMostEvery", 123, null);

    // Log statements always get logged the first time.
    long nowNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    logger.at(INFO, nowNanos).atMostEvery(1, SECONDS).withInjectedLogSite(logSite).log("LOGGED 1");

    nowNanos += MILLISECONDS.toNanos(100);
    logger
        .at(INFO, nowNanos)
        .atMostEvery(1, SECONDS)
        .withInjectedLogSite(logSite)
        .log("NOT LOGGED");

    nowNanos += MILLISECONDS.toNanos(100);
    logger
        .forceAt(INFO, nowNanos)
        .atMostEvery(1, SECONDS)
        .withInjectedLogSite(logSite)
        .log("LOGGED 2");

    nowNanos += MILLISECONDS.toNanos(100);
    logger
        .at(INFO, nowNanos)
        .atMostEvery(1, SECONDS)
        .withInjectedLogSite(logSite)
        .log("NOT LOGGED");

    assertThat(backend.getLoggedCount()).isEqualTo(2);
    backend.assertLogged(0).hasMessage("LOGGED 1");
    backend.assertLogged(0).metadata().hasSize(1);
    backend
        .assertLogged(0)
        .metadata()
        .containsUniqueEntry(Key.LOG_AT_MOST_EVERY, LogSiteStats.newRateLimitPeriod(1, SECONDS));

    backend.assertLogged(1).hasMessage("LOGGED 2");
    backend.assertLogged(1).metadata().hasSize(1);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.WAS_FORCED, true);
  }

  // These tests verify that the mapping between the logging context and the backend preserves
  // arguments as expected.

  @Test
  public void testExplicitVarargs() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    Object[] args = new Object[] {"foo", "bar", "baz"};
    logger.atInfo().logVarargs("Any message ...", args);

    backend.assertLastLogged().hasArguments("foo", "bar", "baz");
    // Make sure we took a copy of the arguments rather than risk re-using them.
    assertThat(backend.getLoggedCount()).isEqualTo(1);
    assertThat(backend.getLogged(0).getArguments()).isNotSameInstanceAs(args);
  }

  @Test
  public void testNoArguments() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
    logger.atInfo().log();
    backend.assertLastLogged().hasMessage("");
    backend.assertLastLogged().hasArguments();
  }

  @SuppressWarnings("FormatString")
  @Test
  public void testLiteralArgument() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    logger.atInfo().log("Hello %s World");
    backend.assertLastLogged().hasMessage("Hello %s World");
    backend.assertLastLogged().hasArguments();
  }

  @Test
  public void testSingleParameter() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    logger.atInfo().log("Hello %d World", 42);
    backend.assertLastLogged().hasMessage("Hello %d World");
    backend.assertLastLogged().hasArguments(42);
  }

  // Tests that a null literal is passed unmodified to the backend without throwing an exception.
  @Test
  public void testNullLiteral() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    logger.atInfo().log((String) null);
    backend.assertLastLogged().hasMessage(null);
  }

  // Tests that null arguments are passed unmodified to the backend without throwing an exception.
  @Test
  public void testNullArgument() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    logger.atInfo().log("Hello %d World", null);
    backend.assertLastLogged().hasMessage("Hello %d World");
    backend.assertLastLogged().hasArguments(new Object[] {null});
  }

  // Currently having a null message and a null argument will throw a runtime exception, but
  // perhaps it shouldn't (it could come from data). In general it is expected that when there are
  // arguments to a log statement the message is a literal, which makes this situation very
  // unlikely and probably a code bug (but even then throwing an exception is something that will
  // only happen when the log statement is enabled).
  // TODO(dbeaumont): Consider allowing this case to work without throwing a runtime exception.
  @Test
  public void testNullMessageAndArgument() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    try {
      logger.atInfo().log(null, null);
      fail("null message and arguments should fail");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testManyObjectParameters() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    String ms = "Any message will do...";

    // Verify that the arguments passed in to the Object based methods are mapped correctly.
    logger.atInfo().log(ms, "1");
    backend.assertLastLogged().hasArguments("1");
    logger.atInfo().log(ms, "1", "2");
    backend.assertLastLogged().hasArguments("1", "2");
    logger.atInfo().log(ms, "1", "2", "3");
    backend.assertLastLogged().hasArguments("1", "2", "3");
    logger.atInfo().log(ms, "1", "2", "3", "4");
    backend.assertLastLogged().hasArguments("1", "2", "3", "4");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5");
    backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6");
    backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7");
    backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8");
    backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7", "8");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9");
    backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    backend.assertLastLogged().hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
    backend
        .assertLastLogged()
        .hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
    logger.atInfo().log(ms, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    backend
        .assertLastLogged()
        .hasArguments("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
  }

  @Test
  public void testOneUnboxedArgument() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    String ms = "Any message will do...";

    // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
    logger.atInfo().log(ms, BYTE_ARG);
    backend.assertLastLogged().hasArguments(BYTE_ARG);
    logger.atInfo().log(ms, SHORT_ARG);
    backend.assertLastLogged().hasArguments(SHORT_ARG);
    logger.atInfo().log(ms, INT_ARG);
    backend.assertLastLogged().hasArguments(INT_ARG);
    logger.atInfo().log(ms, LONG_ARG);
    backend.assertLastLogged().hasArguments(LONG_ARG);
    logger.atInfo().log(ms, CHAR_ARG);
    backend.assertLastLogged().hasArguments(CHAR_ARG);
  }

  @Test
  public void testTwoUnboxedArguments() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    String ms = "Any message will do...";

    // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
    logger.atInfo().log(ms, BYTE_ARG, BYTE_ARG);
    backend.assertLastLogged().hasArguments(BYTE_ARG, BYTE_ARG);
    logger.atInfo().log(ms, BYTE_ARG, SHORT_ARG);
    backend.assertLastLogged().hasArguments(BYTE_ARG, SHORT_ARG);
    logger.atInfo().log(ms, BYTE_ARG, INT_ARG);
    backend.assertLastLogged().hasArguments(BYTE_ARG, INT_ARG);
    logger.atInfo().log(ms, BYTE_ARG, LONG_ARG);
    backend.assertLastLogged().hasArguments(BYTE_ARG, LONG_ARG);
    logger.atInfo().log(ms, BYTE_ARG, CHAR_ARG);
    backend.assertLastLogged().hasArguments(BYTE_ARG, CHAR_ARG);

    logger.atInfo().log(ms, SHORT_ARG, BYTE_ARG);
    backend.assertLastLogged().hasArguments(SHORT_ARG, BYTE_ARG);
    logger.atInfo().log(ms, SHORT_ARG, SHORT_ARG);
    backend.assertLastLogged().hasArguments(SHORT_ARG, SHORT_ARG);
    logger.atInfo().log(ms, SHORT_ARG, INT_ARG);
    backend.assertLastLogged().hasArguments(SHORT_ARG, INT_ARG);
    logger.atInfo().log(ms, SHORT_ARG, LONG_ARG);
    backend.assertLastLogged().hasArguments(SHORT_ARG, LONG_ARG);
    logger.atInfo().log(ms, SHORT_ARG, CHAR_ARG);
    backend.assertLastLogged().hasArguments(SHORT_ARG, CHAR_ARG);

    logger.atInfo().log(ms, INT_ARG, BYTE_ARG);
    backend.assertLastLogged().hasArguments(INT_ARG, BYTE_ARG);
    logger.atInfo().log(ms, INT_ARG, SHORT_ARG);
    backend.assertLastLogged().hasArguments(INT_ARG, SHORT_ARG);
    logger.atInfo().log(ms, INT_ARG, INT_ARG);
    backend.assertLastLogged().hasArguments(INT_ARG, INT_ARG);
    logger.atInfo().log(ms, INT_ARG, LONG_ARG);
    backend.assertLastLogged().hasArguments(INT_ARG, LONG_ARG);
    logger.atInfo().log(ms, INT_ARG, CHAR_ARG);
    backend.assertLastLogged().hasArguments(INT_ARG, CHAR_ARG);

    logger.atInfo().log(ms, LONG_ARG, BYTE_ARG);
    backend.assertLastLogged().hasArguments(LONG_ARG, BYTE_ARG);
    logger.atInfo().log(ms, LONG_ARG, SHORT_ARG);
    backend.assertLastLogged().hasArguments(LONG_ARG, SHORT_ARG);
    logger.atInfo().log(ms, LONG_ARG, INT_ARG);
    backend.assertLastLogged().hasArguments(LONG_ARG, INT_ARG);
    logger.atInfo().log(ms, LONG_ARG, LONG_ARG);
    backend.assertLastLogged().hasArguments(LONG_ARG, LONG_ARG);
    logger.atInfo().log(ms, LONG_ARG, CHAR_ARG);
    backend.assertLastLogged().hasArguments(LONG_ARG, CHAR_ARG);

    logger.atInfo().log(ms, CHAR_ARG, BYTE_ARG);
    backend.assertLastLogged().hasArguments(CHAR_ARG, BYTE_ARG);
    logger.atInfo().log(ms, CHAR_ARG, SHORT_ARG);
    backend.assertLastLogged().hasArguments(CHAR_ARG, SHORT_ARG);
    logger.atInfo().log(ms, CHAR_ARG, INT_ARG);
    backend.assertLastLogged().hasArguments(CHAR_ARG, INT_ARG);
    logger.atInfo().log(ms, CHAR_ARG, LONG_ARG);
    backend.assertLastLogged().hasArguments(CHAR_ARG, LONG_ARG);
    logger.atInfo().log(ms, CHAR_ARG, CHAR_ARG);
    backend.assertLastLogged().hasArguments(CHAR_ARG, CHAR_ARG);
  }

  @Test
  public void testTwoMixedArguments() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    String ms = "Any message will do...";

    // Verify arguments passed in to the non-boxed fundamental type methods are mapped correctly.
    logger.atInfo().log(ms, OBJECT_ARG, BYTE_ARG);
    backend.assertLastLogged().hasArguments(OBJECT_ARG, BYTE_ARG);
    logger.atInfo().log(ms, OBJECT_ARG, SHORT_ARG);
    backend.assertLastLogged().hasArguments(OBJECT_ARG, SHORT_ARG);
    logger.atInfo().log(ms, OBJECT_ARG, INT_ARG);
    backend.assertLastLogged().hasArguments(OBJECT_ARG, INT_ARG);
    logger.atInfo().log(ms, OBJECT_ARG, LONG_ARG);
    backend.assertLastLogged().hasArguments(OBJECT_ARG, LONG_ARG);
    logger.atInfo().log(ms, OBJECT_ARG, CHAR_ARG);
    backend.assertLastLogged().hasArguments(OBJECT_ARG, CHAR_ARG);

    logger.atInfo().log(ms, BYTE_ARG, OBJECT_ARG);
    backend.assertLastLogged().hasArguments(BYTE_ARG, OBJECT_ARG);
    logger.atInfo().log(ms, SHORT_ARG, OBJECT_ARG);
    backend.assertLastLogged().hasArguments(SHORT_ARG, OBJECT_ARG);
    logger.atInfo().log(ms, INT_ARG, OBJECT_ARG);
    backend.assertLastLogged().hasArguments(INT_ARG, OBJECT_ARG);
    logger.atInfo().log(ms, LONG_ARG, OBJECT_ARG);
    backend.assertLastLogged().hasArguments(LONG_ARG, OBJECT_ARG);
    logger.atInfo().log(ms, CHAR_ARG, OBJECT_ARG);
    backend.assertLastLogged().hasArguments(CHAR_ARG, OBJECT_ARG);
  }

  @Test
  public void testWithStackTrace() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    // Keep these 2 lines immediately adjacent to each other.
    StackTraceElement expectedCaller = getCallerInfoFollowingLine();
    logger.atSevere().withStackTrace(StackSize.FULL).log("Answer=%#x", 66);

    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLogged(0).hasMessage("Answer=%#x");
    backend.assertLogged(0).hasArguments(66);
    backend.assertLogged(0).metadata().hasSize(1);
    backend.assertLogged(0).metadata().keys().contains(Key.LOG_CAUSE);

    Throwable cause = backend.getLogged(0).getMetadata().findValue(Key.LOG_CAUSE);
    assertThat(cause).hasMessageThat().isEqualTo("FULL");
    assertThat(cause.getCause()).isNull();

    List<StackTraceElement> actualStack = Arrays.asList(cause.getStackTrace());
    List<StackTraceElement> expectedStack = Arrays.asList(new Throwable().getStackTrace());
    // Overwrite the first element to the expected value.
    expectedStack.set(0, expectedCaller);
    assertThat(actualStack).containsExactlyElementsIn(expectedStack).inOrder();
  }

  @Test
  public void testWithStackTraceAndCause() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    RuntimeException badness = new RuntimeException("badness");

    // Use "SMALL" size here because we rely on the total stack depth in this test being bigger
    // than that. Using "MEDIUM" or "LARGE" might cause the test to fail when verifying the
    // truncated stack size.
    logger.atInfo().withStackTrace(StackSize.SMALL).withCause(badness).log("Answer=%#x", 66);

    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLogged(0).hasMessage("Answer=%#x");
    backend.assertLogged(0).hasArguments(66);
    backend.assertLogged(0).metadata().hasSize(1);
    backend.assertLogged(0).metadata().keys().contains(Key.LOG_CAUSE);

    Throwable cause = backend.getLogged(0).getMetadata().findValue(Key.LOG_CAUSE);
    assertThat(cause).hasMessageThat().isEqualTo("SMALL");
    assertThat(cause.getStackTrace().length).isEqualTo(StackSize.SMALL.getMaxDepth());
    assertThat(cause.getCause()).isEqualTo(badness);
  }

  // See b/27310448.
  @Test
  public void testStackTraceFormatting() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    // Keep these 2 lines immediately adjacent to each other.
    StackTraceElement expectedCaller = getCallerInfoFollowingLine();
    logger.atWarning().withStackTrace(StackSize.SMALL).log("Message");

    // Print the stack trace via the expected method (ie, printStackTrace()).
    Throwable cause = backend.getLogged(0).getMetadata().findValue(Key.LOG_CAUSE);
    assertThat(cause).hasMessageThat().isEqualTo("SMALL");
    ;
    StringWriter out = new StringWriter();
    cause.printStackTrace(new PrintWriter(out));
    Iterable<String> actualStackLines = Splitter.on('\n').trimResults().split(out.toString());

    // We assume there's at least one element in the stack we're testing.
    int syntheticStackSize = cause.getStackTrace().length;
    assertThat(syntheticStackSize).isGreaterThan(0);

    List<StackTraceElement> expectedElements = Arrays.asList(new Throwable().getStackTrace());
    expectedElements.set(0, expectedCaller);
    // Mimic the standard formatting for stack traces (a bit fragile but at least it's explicit).
    List<String> expectedLines =
        FluentIterable.from(expectedElements)
            // Limit to the number in the synthetic stack trace (which is truncated).
            .limit(syntheticStackSize)
            // Format the elements into something that should match the normal stack formatting.
            // Apologies to whoever has to debug/fix this if it ever breaks :(
            .transform(
                new Function<StackTraceElement, String>() {
                  @Override
                  public String apply(StackTraceElement e) {
                    // Native methods (where line number < 0) are formatted differently.
                    if (e.getLineNumber() >= 0) {
                      return String.format(
                          "at %s.%s(%s:%d)",
                          e.getClassName(), e.getMethodName(), e.getFileName(), e.getLineNumber());
                    } else {
                      return String.format(
                          "at %s.%s(Native Method)", e.getClassName(), e.getMethodName());
                    }
                  }
                })
            .toList();

    // This doesn't check anything about the message that's printed before the stack lines,
    // but that's not the point of this test.
    assertThat(expectedLines).hasSize(syntheticStackSize);
    assertThat(actualStackLines).containsAtLeastElementsIn(expectedLines).inOrder();
  }

  private static StackTraceElement getCallerInfoFollowingLine() {
    StackTraceElement caller = new Exception().getStackTrace()[1];
    return new StackTraceElement(
        caller.getClassName(),
        caller.getMethodName(),
        caller.getFileName(),
        caller.getLineNumber() + 1);
  }

  @Test
  public void testExplicitLogSiteInjection() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    for (int i = 0; i <= 6; i++) {
      logHelper(logger, logSite(), 2, "Foo: " + i);
      logHelper(logger, logSite(), 3, "Bar: " + i);
    }
    // Expect: Foo -> 0, 2, 4, 6 and Bar -> 0, 3, 6 (but not in that order)
    assertThat(backend.getLoggedCount()).isEqualTo(7);
    backend.assertLogged(0).hasMessage("Foo: 0");
    backend.assertLogged(1).hasMessage("Bar: 0");
    backend.assertLogged(2).hasMessage("Foo: 2");
    backend.assertLogged(3).hasMessage("Bar: 3");
    backend.assertLogged(4).hasMessage("Foo: 4");
    backend.assertLogged(5).hasMessage("Foo: 6");
    backend.assertLogged(6).hasMessage("Bar: 6");
  }

  // In normal use, the logger would never need to be passed in and you'd use logVarargs().
  private static void logHelper(FluentLogger logger, LogSite logSite, int n, String message) {
    logger.atInfo().withInjectedLogSite(logSite).every(n).log(message);
  }
}
