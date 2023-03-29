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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.flogger.LogContextTest.LogType.BAR;
import static com.google.common.flogger.LogContextTest.LogType.FOO;
import static com.google.common.flogger.LogPerBucketingStrategy.byClass;
import static com.google.common.flogger.LogSites.logSite;
import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;
import static org.junit.Assert.fail;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
import com.google.common.flogger.DurationRateLimiter.RateLimitPeriod;
import com.google.common.flogger.LogContext.Key;
import com.google.common.flogger.context.Tags;
import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeLoggerBackend;
import com.google.common.flogger.testing.FakeMetadata;
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

  private static final MetadataKey<String> REPEATED_KEY = MetadataKey.repeated("str", String.class);
  private static final MetadataKey<Boolean> FLAG_KEY = MetadataKey.repeated("flag", Boolean.class);

  private static final RateLimitPeriod ONCE_PER_SECOND =
      DurationRateLimiter.newRateLimitPeriod(1, SECONDS);

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
  public void testMetadataKeys() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    logger.atInfo().with(REPEATED_KEY, "foo").with(REPEATED_KEY, "bar").log("Several values");
    logger.atInfo().with(FLAG_KEY).log("Set Flag");
    logger.atInfo().with(FLAG_KEY, false).log("No flag");
    logger.atInfo().with(REPEATED_KEY, "foo").with(FLAG_KEY).with(REPEATED_KEY, "bar").log("...");

    assertThat(backend.getLoggedCount()).isEqualTo(4);
    backend.assertLogged(0).metadata().containsEntries(REPEATED_KEY, "foo", "bar");
    backend.assertLogged(1).metadata().containsUniqueEntry(FLAG_KEY, true);
    backend.assertLogged(2).metadata().containsUniqueEntry(FLAG_KEY, false);
    // Just check nothing weird happens when the metadata is interleaved in the log statement.
    backend.assertLogged(3).metadata().containsEntries(REPEATED_KEY, "foo", "bar");
    backend.assertLogged(3).metadata().containsUniqueEntry(FLAG_KEY, true);
  }

  // For testing that log-site tags are correctly merged with metadata, see
  // AbstractScopedLoggingContextTest.
  @Test
  public void testLoggedTags() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    Tags tags = Tags.of("foo", "bar");
    logger.atInfo().with(Key.TAGS, tags).log("With tags");

    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.TAGS, tags);
  }

  @Test
  public void testEveryN() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    long startNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    // Logging occurs for counts: 0, 5, 10 (timestamp is not important).
    for (int millis = 0, count = 0; millis <= 1000; millis += 100) {
      long timestampNanos = startNanos + MILLISECONDS.toNanos(millis);
      logger.at(INFO, timestampNanos).every(5).log("Count=%d", count++);
    }

    assertThat(backend.getLoggedCount()).isEqualTo(3);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_EVERY_N, 5);
    // Check the first log we captured was the first one emitted.
    backend.assertLogged(0).timestampNanos().isEqualTo(startNanos);

    // Check the expected count and skipped-count for each log.
    backend.assertLogged(0).hasArguments(0);
    backend.assertLogged(0).metadata().keys().doesNotContain(Key.SKIPPED_LOG_COUNT);
    backend.assertLogged(1).hasArguments(5);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.SKIPPED_LOG_COUNT, 4);
    backend.assertLogged(2).hasArguments(10);
    backend.assertLogged(2).metadata().containsUniqueEntry(Key.SKIPPED_LOG_COUNT, 4);
  }

  @Test
  public void testOnAverageEveryN() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    long startNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    // Logging occurs randomly 1-in-5 times over 1000 log statements.
    for (int millis = 0, count = 0; millis <= 1000; millis += 1) {
      long timestampNanos = startNanos + MILLISECONDS.toNanos(millis);
      logger.at(INFO, timestampNanos).onAverageEvery(5).log("Count=%d", count++);
    }

    // Satisically impossible that we randomly get +/- 100 over 1000 logs.
    assertThat(backend.getLoggedCount()).isIn(Range.closed(100, 300));
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_SAMPLE_EVERY_N, 5);

    // Check the expected count and skipped-count for each log based on the timestamp.
    int lastLogIndex = -1;
    for (int n = 0; n < backend.getLoggedCount(); n++) {
      // The timestamp increases by 1 millisecond each time so we can get the log index from it.
      long deltaNanos = backend.getLogged(n).getTimestampNanos() - startNanos;
      int logIndex = (int) (deltaNanos / MILLISECONDS.toNanos(1));
      backend.assertLogged(n).hasArguments(logIndex);
      // This works even if lastLogIndex == -1.
      int skipped = (logIndex - lastLogIndex) - 1;
      if (skipped == 0) {
        backend.assertLogged(n).metadata().keys().doesNotContain(Key.SKIPPED_LOG_COUNT);
      } else {
        backend.assertLogged(n).metadata().containsUniqueEntry(Key.SKIPPED_LOG_COUNT, skipped);
      }
      lastLogIndex = logIndex;
    }
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
    RateLimitPeriod rateLimit = DurationRateLimiter.newRateLimitPeriod(2, SECONDS);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, rateLimit);
    // Check the first log we captured was the first one emitted.
    backend.assertLogged(0).timestampNanos().isEqualTo(startNanos);

    // Check the expected count and skipped-count for each log.
    backend.assertLogged(0).hasArguments(0);
    backend.assertLogged(0).metadata().keys().doesNotContain(Key.SKIPPED_LOG_COUNT);
    backend.assertLogged(1).hasArguments(4);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.SKIPPED_LOG_COUNT, 3);
    backend.assertLogged(2).hasArguments(8);
    backend.assertLogged(2).metadata().containsUniqueEntry(Key.SKIPPED_LOG_COUNT, 3);
  }

  @Test
  public void testMultipleRateLimiters_higherLoggingRate() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    // 10 logs per second over 6 seconds.
    long startNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    for (int millis = 0, count = 0; millis <= 6000; millis += 100) {
      long timestampNanos = startNanos + MILLISECONDS.toNanos(millis);
      // More than N logs occur per rate limit period, so logging should occur every 2 seconds.
      logger.at(INFO, timestampNanos).every(15).atMostEvery(2, SECONDS).log("Count=%d", count++);
    }
    assertThat(backend.getLoggedCount()).isEqualTo(4);
    backend.assertLogged(0).hasArguments(0);
    backend.assertLogged(1).hasArguments(20);
    backend.assertLogged(2).hasArguments(40);
    backend.assertLogged(3).hasArguments(60);
    backend.assertLogged(3).metadata().containsUniqueEntry(Key.SKIPPED_LOG_COUNT, 19);
  }

  @Test
  public void testMultipleRateLimiters_lowerLoggingRate() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    // 10 logs per second over 6 seconds.
    long startNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    for (int millis = 0, count = 0; millis <= 6000; millis += 100) {
      long timestampNanos = startNanos + MILLISECONDS.toNanos(millis);
      // Fever than N logs occur in the rate limit period, so logging should occur every 15 logs.
      logger.at(INFO, timestampNanos).every(15).atMostEvery(1, SECONDS).log("Count=%d", count++);
    }
    assertThat(backend.getLoggedCount()).isEqualTo(5);
    backend.assertLogged(0).hasArguments(0);
    backend.assertLogged(1).hasArguments(15);
    backend.assertLogged(2).hasArguments(30);
    backend.assertLogged(3).hasArguments(45);
    backend.assertLogged(4).hasArguments(60);
    backend.assertLogged(4).metadata().containsUniqueEntry(Key.SKIPPED_LOG_COUNT, 14);
  }

  @Test
  public void testPer_withStrategy() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    // Logs for both types should appear (even though the 2nd log is within the rate limit period).
    // NOTE: It is important this is tested on a single log statement.
    long nowNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    for (Throwable err :
        Arrays.asList(
            new IllegalArgumentException(),
            new NullPointerException(),
            new NullPointerException(),
            new IllegalArgumentException())) {
      logger
          .at(INFO, nowNanos)
          .atMostEvery(1, SECONDS)
          .per(err, byClass())
          .log("Err: %s", err.getMessage());
      nowNanos += MILLISECONDS.toNanos(100);
    }

    assertThat(backend.getLoggedCount()).isEqualTo(2);

    // Rate limit period and the aggregation key from "per"
    backend.assertLogged(0).metadata().hasSize(2);
    backend
        .assertLogged(0)
        .metadata()
        .containsUniqueEntry(Key.LOG_SITE_GROUPING_KEY, IllegalArgumentException.class);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND);

    backend.assertLogged(1).metadata().hasSize(2);
    backend
        .assertLogged(1)
        .metadata()
        .containsUniqueEntry(Key.LOG_SITE_GROUPING_KEY, NullPointerException.class);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND);
  }

  // Non-private to allow static import to keep test code concise.
  enum LogType {
    FOO,
    BAR
  };

  @Test
  public void testPer_enum() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    // Logs for both types should appear (even though the 2nd log is within the rate limit period).
    // NOTE: It is important this is tested on a single log statement.
    long nowNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    for (LogType type : Arrays.asList(FOO, FOO, FOO, BAR, FOO, BAR, FOO)) {
      logger.at(INFO, nowNanos).atMostEvery(1, SECONDS).per(type).log("Type: %s", type);
      nowNanos += MILLISECONDS.toNanos(100);
    }

    assertThat(backend.getLoggedCount()).isEqualTo(2);

    // Rate limit period and the aggregation key from "per"
    backend.assertLogged(0).hasArguments(FOO);
    backend.assertLogged(0).metadata().hasSize(2);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_SITE_GROUPING_KEY, FOO);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND);

    backend.assertLogged(1).hasArguments(BAR);
    backend.assertLogged(1).metadata().hasSize(2);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.LOG_SITE_GROUPING_KEY, BAR);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND);
  }

  @Test
  public void testPer_scopeProvider() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    // We can't test a specific implementation of ScopedLoggingContext here (there might not be one
    // available), so we fake it. The ScopedLoggingContext behaviour is well tested elsewhere. Only
    // tests should ever create "immediate providers" like this as it doesn't make sense otherwise.
    LoggingScope fooScope = LoggingScope.create("foo");
    LoggingScope barScope = LoggingScope.create("bar");
    LoggingScopeProvider foo = () -> fooScope;
    LoggingScopeProvider bar = () -> barScope;

    // Logs for both scopes should appear (even though the 2nd log is within the rate limit period).
    // NOTE: It is important this is tested on a single log statement.
    long nowNanos = MILLISECONDS.toNanos(System.currentTimeMillis());
    for (LoggingScopeProvider sp : Arrays.asList(foo, foo, foo, bar, foo, bar, foo)) {
      logger.at(INFO, nowNanos).atMostEvery(1, SECONDS).per(sp).log("message");
      nowNanos += MILLISECONDS.toNanos(100);
    }

    assertThat(backend.getLoggedCount()).isEqualTo(2);

    // Rate limit period and the aggregation key from "per"
    backend.assertLogged(0).metadata().hasSize(2);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_SITE_GROUPING_KEY, fooScope);
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND);

    backend.assertLogged(1).metadata().hasSize(2);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.LOG_SITE_GROUPING_KEY, barScope);
    backend.assertLogged(1).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND);
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
    backend.assertLogged(0).metadata().containsUniqueEntry(Key.LOG_AT_MOST_EVERY, ONCE_PER_SECOND);

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

    Object[] args = new Object[] {"foo", null, "baz"};
    logger.atInfo().logVarargs("Any message ...", args);

    backend.assertLastLogged().hasArguments("foo", null, "baz");
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

  @SuppressWarnings({"FormatString", "OrphanedFormatString"})
  @Test
  public void testLiteralArgument_doesNotEscapePercent() {
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
    // We want to call log(String) (not log(Object)) with a null value.
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
    // Use string representation for comparison since synthetic stack elements are not "equal" to
    // equivalent system stack elements.
    assertThat(actualStack)
        .comparingElementsUsing(transforming(Object::toString, Object::toString, "toString"))
        .containsExactlyElementsIn(expectedStack)
        .inOrder();
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
    logger.atWarning().withStackTrace(StackSize.MEDIUM).log("Message");

    // Print the stack trace via the expected method (ie, printStackTrace()).
    Throwable cause = backend.getLogged(0).getMetadata().findValue(Key.LOG_CAUSE);
    assertThat(cause).hasMessageThat().isEqualTo("MEDIUM");
    StringWriter out = new StringWriter();
    cause.printStackTrace(new PrintWriter(out));
    List<String> stackLines = Splitter.on('\n').trimResults().splitToList(out.toString());
    ImmutableList<String> actualStackRefs =
        stackLines.stream()
            // Ignore lines that don't look like call-stack entries.
            .filter(s -> s.startsWith("at "))
            // Remove anything that's not caller information.
            .map(s -> s.replaceAll("^at (?:java\\.base/)?", ""))
            .collect(toImmutableList());

    // We assume there's at least one element in the stack we're testing.
    assertThat(actualStackRefs).isNotEmpty();

    StackTraceElement[] expectedElements = new Throwable().getStackTrace();
    // Overwrite first element since we are starting from a different place (in the same method).
    expectedElements[0] = expectedCaller;
    // Mimic the standard formatting for stack traces (a bit fragile but at least it's explicit).
    List<String> expectedStackRefs =
        Arrays.stream(expectedElements)
            // Format the elements into something that should match the normal stack formatting.
            // Apologies to whoever has to debug/fix this if it ever breaks :(
            // Native methods (where line number < 0) are formatted differently.
            .map(
                e ->
                    (e.getLineNumber() >= 0)
                        ? String.format(
                            "%s.%s(%s:%d)",
                            e.getClassName(), e.getMethodName(), e.getFileName(), e.getLineNumber())
                        : String.format(
                            "%s.%s(Native Method)", e.getClassName(), e.getMethodName()))
            // Limit to the number in the synthetic stack trace (which is truncated).
            .limit(actualStackRefs.size())
            .collect(toImmutableList());

    // This doesn't check anything about the message that's printed before the stack lines,
    // but that's not the point of this test.
    assertThat(actualStackRefs).isEqualTo(expectedStackRefs);
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
    // Tests it's the log site instance that controls rate limiting, even over different calls.
    // We don't expect this to ever happen in real code though.
    for (int i = 0; i <= 6; i++) {
      logHelper(logger, logSite(), 2, "Foo: " + i); // Log every 2nd (0, 2, 4, 6)
      logHelper(logger, logSite(), 3, "Bar: " + i); // Log every 3rd (0, 3, 6)
    }
    // Expect: Foo -> 0, 2, 4, 6 and Bar -> 0, 3, 6 (but not in that order)
    assertThat(backend.getLoggedCount()).isEqualTo(7);
    backend.assertLogged(0).hasArguments("Foo: 0");
    backend.assertLogged(1).hasArguments("Bar: 0");
    backend.assertLogged(2).hasArguments("Foo: 2");
    backend.assertLogged(3).hasArguments("Bar: 3");
    backend.assertLogged(4).hasArguments("Foo: 4");
    backend.assertLogged(5).hasArguments("Foo: 6");
    backend.assertLogged(6).hasArguments("Bar: 6");
  }

  // In normal use, the logger would never need to be passed in and you'd use logVarargs().
  private static void logHelper(FluentLogger logger, LogSite logSite, int n, String message) {
    logger.atInfo().withInjectedLogSite(logSite).every(n).log("%s", message);
  }

  // It's important that injecting an INVALID log site acts as a override to suppress log site
  // calculation rather than being a no-op.
  @Test
  public void testExplicitLogSiteSuppression() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);

    logger.atInfo().withInjectedLogSite(LogSite.INVALID).log("No log site here");
    logger.atInfo().withInjectedLogSite(null).log("No-op injection");

    assertThat(backend.getLoggedCount()).isEqualTo(2);
    backend.assertLogged(0).logSite().isEqualTo(LogSite.INVALID);

    backend.assertLogged(1).logSite().isNotNull();
    backend.assertLogged(1).logSite().isNotEqualTo(LogSite.INVALID);
  }

  @Test
  public void testLogSiteSpecializationSameMetadata() {
    FakeMetadata fooMetadata = new FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo");

    LogSite logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>");
    LogSiteKey fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata);

    assertThat(fooKey).isEqualTo(LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata));
  }

  @Test
  public void testLogSiteSpecializationKeyCountMatters() {
    FakeMetadata fooMetadata = new FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo");
    FakeMetadata repeatedMetadata =
        new FakeMetadata()
            .add(Key.LOG_SITE_GROUPING_KEY, "foo")
            .add(Key.LOG_SITE_GROUPING_KEY, "foo");

    LogSite logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>");
    LogSiteKey fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata);
    LogSiteKey repeatedKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, repeatedMetadata);

    assertThat(fooKey).isNotEqualTo(repeatedKey);
  }

  @Test
  public void testLogSiteSpecializationDifferentKeys() {
    FakeMetadata fooMetadata = new FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "foo");
    FakeMetadata barMetadata = new FakeMetadata().add(Key.LOG_SITE_GROUPING_KEY, "bar");

    LogSite logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>");
    LogSiteKey fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata);
    LogSiteKey barKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, barMetadata);

    assertThat(fooKey).isNotEqualTo(barKey);
  }

  // This is unfortunate but hard to work around unless SpecializedLogSiteKey can be made invariant
  // to the order of specialization (but this class must be very efficient, so that would be hard).
  // This should not be an issue in expected use, since specialization keys should always be applied
  // in the same order at any given log statement.
  @Test
  public void testLogSiteSpecializationOrderMatters() {
    FakeMetadata fooBarMetadata =
        new FakeMetadata()
            .add(Key.LOG_SITE_GROUPING_KEY, "foo")
            .add(Key.LOG_SITE_GROUPING_KEY, "bar");
    FakeMetadata barFooMetadata =
        new FakeMetadata()
            .add(Key.LOG_SITE_GROUPING_KEY, "bar")
            .add(Key.LOG_SITE_GROUPING_KEY, "foo");

    LogSite logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>");
    LogSiteKey fooBarKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooBarMetadata);
    LogSiteKey barFooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, barFooMetadata);

    assertThat(fooBarKey).isNotEqualTo(barFooKey);
  }

  @Test
  public void testLogSiteSpecializationKey() {
    Key.LOG_SITE_GROUPING_KEY.emitRepeated(
        Iterators.<Object>forArray("foo"),
        (k, v) -> {
          assertThat(k).isEqualTo("group_by");
          assertThat(v).isEqualTo("foo");
        });

    // We don't care too much about the case with multiple keys since it's so rare, but it should
    // be vaguely sensible.
    Key.LOG_SITE_GROUPING_KEY.emitRepeated(
        Iterators.<Object>forArray("foo", "bar"),
        (k, v) -> {
          assertThat(k).isEqualTo("group_by");
          assertThat(v).isEqualTo("[foo,bar]");
        });
  }
}
