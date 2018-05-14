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

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.SimpleMessageFormatter.SimpleLogHandler;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/** Class that represents a log entry that can be written to log4j. */
final class SimpleLogEvent implements SimpleLogHandler {
  /** Creates a {@link SimpleLogEvent} for a normal log statement from the given data. */
  static SimpleLogEvent create(Logger logger, LogData data) {
    return new SimpleLogEvent(logger, data);
  }

  /** Creates a {@link SimpleLogEvent} in the case of an error during logging. */
  static SimpleLogEvent error(Logger logger, RuntimeException error, LogData data) {
    return new SimpleLogEvent(logger, error, data);
  }

  private final Logger logger;
  private final LogData logData;

  private Level level;
  private String message;
  private Throwable thrown;

  private SimpleLogEvent(Logger logger, LogData logData) {
    this.logger = logger;
    this.logData = logData;
    LogDataFormatter.format(logData, this);
  }

  private SimpleLogEvent(Logger logger, RuntimeException error, LogData badLogData) {
    this.logger = logger;
    this.logData = badLogData;
    LogDataFormatter.formatBadLogData(error, badLogData, this);
  }

  @Override
  public void handleFormattedLogMessage(
      java.util.logging.Level level, String message, Throwable thrown) {
    this.level = Log4jLoggerBackend.toLog4jLevel(level);
    this.message = message;
    this.thrown = thrown;
  }

  Level getLevel() {
    return level;
  }

  LoggingEvent asLoggingEvent() {
    // The fully qualified class name of the logger instance is normally used to compute the log
    // location (file, class, method, line number) from the stacktrace. Since we already have the
    // log location in hand we don't need this computation. By passing in null as fully qualified
    // class name of the logger instance we ensure that the log location computation is disabled.
    // this is important since the log location computation is very expensive.
    String fqnOfCategoryClass = null;

    // The Nested Diagnostic Context (NDC) allows to include additional metadata into logs which
    // are written from the current thread.
    //
    // Example:
    //  NDC.push("user.id=" + userId);
    //  // do business logic that triggers logs
    //  NDC.pop();
    //  NDC.remove();
    //
    // By using '%x' in the ConversionPattern of an appender this data can be included in the logs.
    //
    // We could include this data here by doing 'NDC.get()', but we don't want to encourage people
    // using the log4j specific NDC. Instead this should be supported by a LoggingContext and usage
    // of Flogger tags.
    String nestedDiagnosticContext = "";

    // The Mapped Diagnostic Context (MDC) allows to include additional metadata into logs which
    // are written from the current thread.
    //
    // Example:
    //  MDC.put("user.id", userId);
    //  // do business logic that triggers logs
    //  MDC.clear();
    //
    // By using '%X{key}' in the ConversionPattern of an appender this data can be included in the
    // logs.
    //
    // We could include this data here by doing 'MDC.getContext()', but we don't want to encourage
    // people using the log4j specific MDC. Instead this should be supported by a LoggingContext and
    // usage of Flogger tags.
    ImmutableMap<String, String> mdcProperties = ImmutableMap.of();

    return new LoggingEvent(
        fqnOfCategoryClass,
        logger,
        TimeUnit.NANOSECONDS.toMillis(logData.getTimestampNanos()),
        level,
        message,
        Thread.currentThread().getName(),
        thrown != null ? new ThrowableInformation(thrown) : null,
        nestedDiagnosticContext,
        getLocationInfo(),
        mdcProperties);
  }

  private LocationInfo getLocationInfo() {
    LogSite logSite = logData.getLogSite();
    return new LocationInfo(
        logSite.getFileName(),
        logSite.getClassName(),
        logSite.getMethodName(),
        Integer.toString(logSite.getLineNumber()));
  }

  @Override
  public String toString() {
    // Note that this toString() method is _not_ safe against exceptions thrown by user toString().
    StringBuilder out = new StringBuilder();
    out.append(getClass().getSimpleName()).append(" {\n  message: ").append(message).append('\n');
    LogDataFormatter.appendLogData(logData, out);
    out.append("\n}");
    return out.toString();
  }
}
