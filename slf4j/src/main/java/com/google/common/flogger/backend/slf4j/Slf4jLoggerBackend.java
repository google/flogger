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

import com.google.common.flogger.LogContext;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.MessageUtils;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.SimpleMessageFormatter;
import java.util.logging.Level;
import org.slf4j.Logger;

/** A logging backend that uses slf4j to output log statements. */
final class Slf4jLoggerBackend extends LoggerBackend {

  // Represents the log levels supported by SLF4J, used to dispatch calls accordingly.
  private enum Slf4jLogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
  }

  // Pre-resolve constant values used in isLoggable() to reduce code size and hopefully get best
  // performance.
  private static final int JDK_ALL_LEVEL_VALUE = Level.ALL.intValue();
  private static final int JDK_OFF_LEVEL_VALUE = Level.OFF.intValue();
  private static final int JDK_FINE_LEVEL_VALUE = Level.FINE.intValue();
  private static final int JDK_INFO_LEVEL_VALUE = Level.INFO.intValue();
  private static final int JDK_WARNING_LEVEL_VALUE = Level.WARNING.intValue();
  private static final int JDK_SEVERE_LEVEL_VALUE = Level.SEVERE.intValue();

  private final Logger logger;

  Slf4jLoggerBackend(Logger logger) {
    if (logger == null) {
      throw new NullPointerException("logger is required");
    }
    this.logger = logger;
  }

  /**
   * Adapts the JUL level to SLF4J level per the below mapping:
   *
   * <table>
   * <tr>
   * <th>JUL</th>
   * <th>SLF4J</th>
   * </tr>
   * <tr>
   * <td>FINEST</td><td>TRACE</td>
   * </tr><tr>
   * <td>FINER</td><td>TRACE</td>
   * </tr>
   * <tr>
   * <td>FINE</td><td>DEBUG</td>
   * </tr>
   * <tr>
   * <td>CONFIG</td><td>DEBUG</td>
   * </tr>
   * <tr>
   * <td>INFO</td>
   * <td>INFO</td>
   * </tr>
   * <tr>
   * <td>WARNING</td>
   * <td>WARN</td>
   * </tr>
   * <tr>
   * <td>SEVERE</td>
   * <td>ERROR</td>
   * </tr>
   * </table>
   *
   * <p>Custom JUL levels are mapped to the next-lowest standard JUL level; for example, a custom
   * level at 750 (between INFO:800 and CONFIG:700) would map to the same as CONFIG (DEBUG).
   *
   * <p>It isn't expected that the JUL levels 'ALL' or 'OFF' are passed into this method; doing so
   * will throw an IllegalArgumentException, as those levels are for configuration, not logging
   *
   * @param level the JUL level to map; any standard or custom JUL level, except for ALL or OFF
   * @return the appropriate Slf4jLogLevel value for the given JUL level; never null.
   */
  private static Slf4jLogLevel mapToSlf4jLogLevel(Level level) {
    // Performance consideration: mapToSlf4jLogLevel is a very hot method, called even when
    // logging is disabled. Allocations (and other latency-introducing constructs) should be avoided
    int requestedLevel = level.intValue();

    // Flogger shouldn't allow ALL or OFF to be used for logging.
    // If Flogger does add this check to the core library it can be removed here (and should be,
    // as this method is on the critical performance path for determining whether log statements
    // are disabled, hence called for all log statements).
    if (requestedLevel == JDK_ALL_LEVEL_VALUE || requestedLevel == JDK_OFF_LEVEL_VALUE) {
      throw new IllegalArgumentException("Unsupported log level: " + level);
    }
    if (requestedLevel < JDK_FINE_LEVEL_VALUE) {
      return Slf4jLogLevel.TRACE;
    }
    if (requestedLevel < JDK_INFO_LEVEL_VALUE) {
      return Slf4jLogLevel.DEBUG;
    }
    if (requestedLevel < JDK_WARNING_LEVEL_VALUE) {
      return Slf4jLogLevel.INFO;
    }
    if (requestedLevel < JDK_SEVERE_LEVEL_VALUE) {
      return Slf4jLogLevel.WARN;
    }
    return Slf4jLogLevel.ERROR;
  }

  @Override
  public String getLoggerName() {
    return logger.getName();
  }

  @Override
  public boolean isLoggable(Level level) {
    // Performance consideration: isLoggable is a very hot method, called even when logging is
    // disabled.  Allocations (and any other latency-introducing constructs) should be avoided
    Slf4jLogLevel slf4jLogLevel = mapToSlf4jLogLevel(level);

    // dispatch to each level-specific method as SLF4J doesn't expose a logger.log( level, ... )
    switch (slf4jLogLevel) {
      case TRACE:
        return logger.isTraceEnabled();
      case DEBUG:
        return logger.isDebugEnabled();
      case INFO:
        return logger.isInfoEnabled();
      case WARN:
        return logger.isWarnEnabled();
      case ERROR:
        return logger.isErrorEnabled();
    }
    throw new AssertionError("Unknown SLF4J log level: " + slf4jLogLevel);
  }

  @Override
  public void log(LogData logData) {
    // Use MetadataProcessor to include context metadata in the formatted log message.
    MetadataProcessor metadata =
        MetadataProcessor.forScopeAndLogSite(Platform.getInjectedMetadata(), logData.getMetadata());
    String message = SimpleMessageFormatter.getDefaultFormatter().format(logData, metadata);

    // The "cause" is null if no cause was given.
    Throwable thrown = metadata.getSingleValue(LogContext.Key.LOG_CAUSE);

    // Dispatch to each level-specific method, as SLF4J doesn't expose a logger.log( level, ... )
    Slf4jLogLevel slf4jLogLevel = mapToSlf4jLogLevel(logData.getLevel());
    switch (slf4jLogLevel) {
      case TRACE:
        logger.trace(message, thrown);
        return;
      case DEBUG:
        logger.debug(message, thrown);
        return;
      case INFO:
        logger.info(message, thrown);
        return;
      case WARN:
        logger.warn(message, thrown);
        return;
      case ERROR:
        logger.error(message, thrown);
        return;
    }
    throw new AssertionError("Unknown SLF4J log level: " + slf4jLogLevel);
  }

  @Override
  public void handleError(RuntimeException exception, LogData badData) {
    StringBuilder errorMsg = new StringBuilder(200);
    errorMsg.append("LOGGING ERROR: ").append(exception.getMessage());
    appendBadLogData(badData, errorMsg);
    // Log at ERROR to ensure visibility.
    logger.error(errorMsg.toString(), exception);
  }

  // Based on com.google.common.flogger.backend.system.AbstractLogRecord.safeAppend.
  private static void appendBadLogData(LogData data, StringBuilder out) {
    out.append("\n  original message: ");
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
    // TODO: Maybe use MetadataProcessor here to include context metadata.
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
