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

import com.google.common.flogger.LogSite;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.SimpleMessageFormatter.SimpleLogHandler;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;

/** Class that represents a log entry that can be written to log4j2. */
final class Log4j2SimpleLogEvent implements SimpleLogHandler {
  /** Creates a {@link Log4j2SimpleLogEvent} for a normal log statement from the given data. */
  static Log4j2SimpleLogEvent create(Logger logger, LogData data) {
    return new Log4j2SimpleLogEvent(logger, data);
  }

  /** Creates a {@link Log4j2SimpleLogEvent} in the case of an error during logging. */
  static Log4j2SimpleLogEvent error(Logger logger, RuntimeException error, LogData data) {
    return new Log4j2SimpleLogEvent(logger, data, error);
  }

  // Note: Currently the logger is only used to set the logger name in the log event and that looks
  // like it might always be identical to the fluent logger name, so this field might be redundant.
  private final Logger logger;
  private final LogData logData;

  // The following fields are set when handleFormattedLogMessage() is called.
  // Level and message will be set to valid values, but the cause is nullable.
  //
  // Note: The log4j level is only used once elsewhere, so it could easily removed to reduce the
  // size of allocations and just recalculated from LogData.
  private Level level = null;
  private String message = null;
  private Throwable thrown = null;

  private Log4j2SimpleLogEvent(Logger logger, LogData logData) {
    this.logger = logger;
    this.logData = logData;
    Log4j2LogDataFormatter.format(logData, this);
  }

  private Log4j2SimpleLogEvent(Logger logger, LogData badLogData, RuntimeException error) {
    this.logger = logger;
    this.logData = badLogData;
    Log4j2LogDataFormatter.formatBadLogData(error, badLogData, this);
  }

  @Override
  public void handleFormattedLogMessage(
      java.util.logging.Level level, String message, Throwable thrown) {
    this.level = Log4j2LoggerBackend.toLog4jLevel(level);
    this.message = message;
    this.thrown = thrown;
  }

  Level getLevel() {
    return level;
  }

  LogEvent asLoggingEvent() {
    // The Mapped Diagnostic Context (MDC) allows to include additional metadata into logs which
    // are written from the current thread.
    //
    // Example:
    //  ThreadContext.put("user.id", userId);
    //  // do business logic that triggers logs
    //  ThreadContext.clear();
    //
    // By using '%X{key}' in the ConversionPattern of an appender this data can be included in the
    // logs.
    //
    // We could include this data here by doing 'MDC.getContext()', but we don't want to encourage
    // people using the log4j specific MDC. Instead this should be supported by a LoggingContext and
    // usage of Flogger tags.
    Map<String, String> mdcProperties = Collections.emptyMap();

    // The fully qualified class name of the logger instance is normally used to compute the log
    // location (file, class, method, line number) from the stacktrace. Since we already have the
    // log location in hand we don't need this computation. By passing in null as fully qualified
    // class name of the logger instance we ensure that the log location computation is disabled.
    // this is important since the log location computation is very expensive.
    return Log4jLogEvent.newBuilder()
        .setLoggerName(logger.toString())
        .setLoggerFqcn(null)
        .setLevel(level)
        .setMessage(new SimpleMessage(message))
        .setThreadName(Thread.currentThread().getName())
        // Don't use Duration here as (a) it allocates and (b) we can't allow error on overflow.
        .setTimeMillis(TimeUnit.NANOSECONDS.toMillis(logData.getTimestampNanos()))
        .setThrown(thrown != null ? Throwables.getRootCause(thrown) : null)
        .setIncludeLocation(true)
        .setSource(getLocationInfo())
        .setContextData(new SortedArrayStringMap(mdcProperties))
        .build();
  }

  private StackTraceElement getLocationInfo() {
    LogSite logSite = logData.getLogSite();
    return new StackTraceElement(
        logSite.getClassName(),
        logSite.getMethodName(),
        logSite.getFileName(),
        logSite.getLineNumber());
  }

  @Override
  public String toString() {
    // Note that this toString() method is _not_ safe against exceptions thrown by user toString().
    StringBuilder out = new StringBuilder();
    out.append(getClass().getSimpleName()).append(" {\n  message: ").append(message).append('\n');
    Log4j2LogDataFormatter.appendLogData(logData, out);
    out.append("\n}");
    return out.toString();
  }
}
