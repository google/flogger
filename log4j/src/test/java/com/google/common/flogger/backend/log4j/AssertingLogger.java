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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Helper class for testing the log4j backend.
 *
 * <p>This class implements a log4j Logger with assertion functionality. It captures the
 * LoggingEvents so that they can be asserted later.
 *
 * <p>The message of the captured LoggingEvents is already formatted (it already contains all
 * arguments and the metadata context was already appended).
 *
 * <p>Note that we do not just capture the LoggingEvent because we know that it's the action of
 * calling some of the getters that causes work to be done (which is what any log handler would be
 * expected to do).
 *
 * <p>Usage:
 *
 * <pre>
 *   AssertingLogger logger = AssertingLogger.createOrGet("foo");
 *   LoggerBackend backend = new Log4jLoggerBackend(logger);
 *   backend.log(FakeLogData.withPrintfStyle("Hello World"));
 *   logger.assertLogCount(1);
 *   logger.assertLogEntry(0, INFO, "Hello World");
 * </pre>
 */
final class AssertingLogger extends Logger {

  private static class LogEntry {
    private final String message;
    private final Level level;
    private final Throwable thrown;

    LogEntry(String message, Level level, Throwable thrown) {
      this.message = message;
      this.level = level;
      this.thrown = thrown;
    }
  }

  /**
   * Returns an AssertionLogger with the given name.
   *
   * <p>If an AssertionLogger with this name was created before than the existing AssertionLogger
   * instance is returned, otherwise a new AssertionLogger is created.
   *
   * <p>It is safe to call this method in parallel tests but it's up to the caller to use unique
   * names if they want unique instances.
   *
   * @throws IllegalStateException if a logger with the given name already exists but is not an
   *     AssertingLogger
   */
  static AssertingLogger createOrGet(String name) {
    Logger logger = LogManager.getLogger(name, AssertingLogger::new);
    checkState(
        logger instanceof AssertingLogger,
        "Logger %s already exists, but is not an AssertingLogger",
        name);
    return (AssertingLogger) logger;
  }

  private final List<AssertingLogger.LogEntry> entries = new ArrayList<>();

  private AssertingLogger(String name) {
    super(name);
  }

  @Override
  public boolean isEnabledFor(Priority level) {
    return true;
  }

  @Override
  public void callAppenders(LoggingEvent event) {
    entries.add(
        new LogEntry(
            event.getRenderedMessage(),
            event.getLevel(),
            event.getThrowableInformation() != null
                ? event.getThrowableInformation().getThrowable()
                : null));
  }

  String getMessage(int index) {
    return entries.get(index).message;
  }

  void assertLogCount(int count) {
    assertThat(entries.size()).isEqualTo(count);
  }

  void assertLogEntry(int index, Level level, String message) {
    AssertingLogger.LogEntry entry = entries.get(index);
    assertThat(entry.level).isEqualTo(level);
    assertThat(entry.message).isEqualTo(message);
    assertThat(entry.thrown).isNull();
  }

  void assertThrown(int index, Throwable thrown) {
    assertThat(entries.get(index).thrown).isSameInstanceAs(thrown);
  }
}
