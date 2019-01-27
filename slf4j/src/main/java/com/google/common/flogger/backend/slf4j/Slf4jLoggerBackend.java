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

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.SimpleMessageFormatter;
import org.slf4j.Logger;

import java.util.logging.Level;

/**
 * A logging backend that uses slf4j to output log statements.
 */
final class Slf4jLoggerBackend extends LoggerBackend implements
    SimpleMessageFormatter.SimpleLogHandler {

  private final Logger logger;

  Slf4jLoggerBackend(Logger logger) {
    if (logger == null) {
      throw new NullPointerException("logger is required");
    }
    this.logger = logger;
  }

  // represents the log levels supported by SLF4J, used internally to dispatch calls accordingly
  private enum Slf4jLogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR
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
   * level at 750 (between INFO:800 and CONFIG:700) would map to the same as CONFIG (DEBUG).</p>
   *
   * <p>It isn't expected that the JUL levels 'ALL' or 'OFF' are passed into this method;
   * doing so will throw an IllegalArgumentException, as those levels are for configuration, not
   * logging</p>
   *
   * @param level the JUL level to map; any standard or custom JUL level, except for ALL or OFF
   * @return the MappedLevel object representing the SLF4J adapters appropriate for the requested
   * log level; never null.
   */
  private static Slf4jLogLevel mapToSlf4jLogLevel(Level level) {
    int requestedLevel = level.intValue();

    if (requestedLevel == Level.ALL.intValue() || requestedLevel == Level.OFF.intValue()) {
      // Flogger doesn't allow ALL or OFF to be used for logging, only for configuration
      throw new IllegalArgumentException("Unsupported log level: " + level);
    }

    // FINEST, FINER -> TRACE
    // custom JUL levels less than FINE -> TRACE
    if (requestedLevel < Level.FINE.intValue()) {
      return Slf4jLogLevel.TRACE;
    }

    // FINE, CONFIG -> DEBUG
    // custom JUL levels less than INFO -> DEBUG
    if (requestedLevel < Level.INFO.intValue()) {
      return Slf4jLogLevel.DEBUG;
    }

    // INFO -> INFO
    // custom JUL levels less than WARNING -> INFO
    if (requestedLevel < Level.WARNING.intValue()) {
      return Slf4jLogLevel.INFO;
    }

    // WARNING -> WARN
    // custom JUL levels less than SEVERE -> WARN
    if (requestedLevel < Level.SEVERE.intValue()) {
      return Slf4jLogLevel.WARN;
    }

    // SEVERE -> ERROR
    // custom JUL levels greater than SEVERE -> ERROR
    return Slf4jLogLevel.ERROR;
  }

  @Override
  public String getLoggerName() {
    return logger.getName();
  }

  @Override
  public boolean isLoggable(Level level) {
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
      default:
        throw new AssertionError("Unknown SLF4J log level: " + slf4jLogLevel);
    }
  }

  @Override
  public void log(LogData data) {
    SimpleMessageFormatter.format(data, this);
  }

  @Override
  public void handleError(RuntimeException exception, LogData badData) {
    // log at WARN to ensure visibility
    logger.warn(String.format("LOGGING ERROR: %s", exception.getMessage()));
  }

  @Override
  public void handleFormattedLogMessage(Level level, String message, Throwable thrown) {
    Slf4jLogLevel slf4jLogLevel = mapToSlf4jLogLevel(level);

    // dispatch to each level-specific method, as SLF4J doesn't expose a logger.log( level, ... )
    switch (slf4jLogLevel) {
      case TRACE:
        logger.trace(message, thrown);
        break;
      case DEBUG:
        logger.debug(message, thrown);
        break;
      case INFO:
        logger.info(message, thrown);
        break;
      case WARN:
        logger.warn(message, thrown);
        break;
      case ERROR:
        logger.error(message, thrown);
        break;
      default:
        throw new AssertionError("Unknown SLF4J log level: " + slf4jLogLevel);
    }
  }
}
