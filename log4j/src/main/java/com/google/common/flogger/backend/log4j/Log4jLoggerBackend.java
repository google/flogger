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

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import org.apache.log4j.Logger;

/** A logging backend that uses log4j to output log statements. */
final class Log4jLoggerBackend extends LoggerBackend {
  /** Converts java.util.logging.Level to org.apache.log4j.Level. */
  static org.apache.log4j.Level toLog4jLevel(java.util.logging.Level level) {
    if (level.intValue() >= java.util.logging.Level.SEVERE.intValue()) {
      return org.apache.log4j.Level.ERROR;
    } else if (level.intValue() >= java.util.logging.Level.WARNING.intValue()) {
      return org.apache.log4j.Level.WARN;
    } else if (level.intValue() >= java.util.logging.Level.INFO.intValue()) {
      return org.apache.log4j.Level.INFO;
    } else if (level.intValue() >= java.util.logging.Level.FINE.intValue()) {
      return org.apache.log4j.Level.DEBUG;
    }
    return org.apache.log4j.Level.TRACE;
  }

  private final Logger logger;

  // VisibleForTesting
  Log4jLoggerBackend(Logger logger) {
    this.logger = logger;
  }

  @Override
  public String getLoggerName() {
    // Logger#getName() returns exactly the name that we used to create the Logger in
    // Log4jBackendFactory. It matches the name of the logging class.
    return logger.getName();
  }

  @Override
  public boolean isLoggable(java.util.logging.Level level) {
    return logger.isEnabledFor(toLog4jLevel(level));
  }

  private void log(SimpleLogEvent logEntry, boolean wasForced) {
    if (wasForced || logger.isEnabledFor(logEntry.getLevel())) {
      forceLog(logger, logEntry);
    }
  }

  @Override
  public void log(LogData logData) {
    log(SimpleLogEvent.create(logger, logData), logData.wasForced());
  }

  @Override
  public void handleError(RuntimeException error, LogData badData) {
    log(SimpleLogEvent.error(logger, error, badData), badData.wasForced());
  }

  private void forceLog(Logger logger, SimpleLogEvent logEntry) {
    // Logger#callAppenders circumvents any evaluation of whether to log or not to log.
    logger.callAppenders(logEntry.asLoggingEvent());
  }
}
