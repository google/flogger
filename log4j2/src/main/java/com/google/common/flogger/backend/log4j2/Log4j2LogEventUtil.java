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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.WARNING;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.MessageUtils;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.common.flogger.backend.SimpleMessageFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.core.time.MutableInstant;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.message.SimpleMessage;

/** Helper to format LogData */
final class Log4j2LogEventUtil {

  private Log4j2LogEventUtil() {}

  static LogEvent toLog4jLogEvent(String loggerName, LogData logData) {
    MetadataProcessor metadata =
        MetadataProcessor.forScopeAndLogSite(Metadata.empty(), logData.getMetadata());
    String message = SimpleMessageFormatter.getDefaultFormatter().format(logData, metadata);
    Throwable thrown = metadata.getSingleValue(LogContext.Key.LOG_CAUSE);
    return toLog4jLogEvent(
        loggerName, logData, message, toLog4jLevel(logData.getLevel()), thrown);
  }

  static LogEvent toLog4jLogEvent(String loggerName, RuntimeException error, LogData badData) {
    String message = formatBadLogData(error, badData);
    // Re-target this log message as a warning (or above) since it indicates a real bug.
    Level level = badData.getLevel().intValue() < WARNING.intValue() ? WARNING : badData.getLevel();
    return toLog4jLogEvent(loggerName, badData, message, toLog4jLevel(level), error);
  }

  private static LogEvent toLog4jLogEvent(
      String loggerName,
      LogData logData,
      String message,
      org.apache.logging.log4j.Level level,
      Throwable thrown) {
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
    Map<String, String> mdcProperties = Collections.emptyMap();

    LogSite logSite = logData.getLogSite();
    StackTraceElement locationInfo =
        new StackTraceElement(
            logSite.getClassName(),
            logSite.getMethodName(),
            logSite.getFileName(),
            logSite.getLineNumber());

    return Log4jLogEvent.newBuilder()
        .setLoggerName(loggerName)
        .setLoggerFqcn(logData.getLoggerName())
        .setLevel(level) // this might be different from logData.getLevel() for errors.
        .setMessage(new SimpleMessage(message))
        .setThreadName(Thread.currentThread().getName())
        .setInstant(getInstant(logData.getTimestampNanos()))
        .setThrown(thrown != null ? Throwables.getRootCause(thrown) : null)
        .setIncludeLocation(true)
        .setSource(locationInfo)
        .setContextMap(mdcProperties)
        .build();
  }

  @SuppressWarnings({"NanosTo_Seconds", "SecondsTo_Nanos"})
  private static Instant getInstant(long timestampNanos) {
    MutableInstant instant = new MutableInstant();
    // Don't use Duration here as (a) it allocates and (b) we can't allow error on overflow.
    long epochSeconds = NANOSECONDS.toSeconds(timestampNanos);
    int remainingNanos = (int) (timestampNanos - SECONDS.toNanos(epochSeconds));
    instant.initFromEpochSecond(epochSeconds, remainingNanos);
    return instant;
  }

  /** Converts java.util.logging.Level to org.apache.log4j.Level. */
  static org.apache.logging.log4j.Level toLog4jLevel(java.util.logging.Level level) {
    int logLevel = level.intValue();
    if (logLevel < java.util.logging.Level.FINE.intValue()) {
      return org.apache.logging.log4j.Level.TRACE;
    } else if (logLevel < java.util.logging.Level.INFO.intValue()) {
      return org.apache.logging.log4j.Level.DEBUG;
    } else if (logLevel < java.util.logging.Level.WARNING.intValue()) {
      return org.apache.logging.log4j.Level.INFO;
    } else if (logLevel < java.util.logging.Level.SEVERE.intValue()) {
      return org.apache.logging.log4j.Level.WARN;
    }
    return org.apache.logging.log4j.Level.ERROR;
  }

  /**
   * Formats the log message in response to an exception during a previous logging attempt. A
   * synthetic error message is generated from the original log data and the given exception is set
   * as the cause. The level of this record is the maximum of WARNING or the original level.
   */
  private static String formatBadLogData(RuntimeException error, LogData badLogData) {
    StringBuilder errorMsg =
        new StringBuilder("LOGGING ERROR: ").append(error.getMessage()).append('\n');
    int length = errorMsg.length();
    try {
      appendLogData(badLogData, errorMsg);
    } catch (RuntimeException e) {
      // Reset partially written buffer when an error occurs.
      errorMsg.setLength(length);
      errorMsg.append("Cannot append LogData: ").append(e);
    }
    return errorMsg.toString();
  }

  /** Appends the given {@link LogData} to the given {@link StringBuilder}. */
  private static void appendLogData(LogData data, StringBuilder out) {
    out.append("  original message: ");
    if (data.getTemplateContext() == null) {
      out.append(data.getLiteralArgument());
    } else {
      // We know that there's at least one argument to display here.
      out.append(data.getTemplateContext().getMessage());
      out.append("\n  original arguments:");
      for (Object arg : data.getArguments()) {
        out.append("\n    ").append(MessageUtils.safeToString(arg));
      }
    }
    Metadata metadata = data.getMetadata();
    if (metadata.size() > 0) {
      out.append("\n  metadata:");
      for (int n = 0; n < metadata.size(); n++) {
        out.append("\n    ");
        out.append(metadata.getKey(n).getLabel()).append(": ").append(metadata.getValue(n));
      }
    }
    out.append("\n  level: ").append(data.getLevel());
    out.append("\n  timestamp (nanos): ").append(data.getTimestampNanos());
    out.append("\n  class: ").append(data.getLogSite().getClassName());
    out.append("\n  method: ").append(data.getLogSite().getMethodName());
    out.append("\n  line number: ").append(data.getLogSite().getLineNumber());
  }
}
