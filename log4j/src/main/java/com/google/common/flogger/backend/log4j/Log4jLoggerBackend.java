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

import static com.google.common.flogger.backend.log4j.Log4jLoggingEventUtil.toLog4jLevel;
import static com.google.common.flogger.backend.log4j.Log4jLoggingEventUtil.toLog4jLoggingEvent;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import org.apache.log4j.Logger;

/**
 * A logging backend that uses log4j to output log statements.
 *
 * <p>Note: This code is mostly derived from the equivalently named class in the Log4j2 backend
 * implementation, and should be kept in-sync with it as far as possible. If possible, any changes
 * to the functionality of this class should first be made in the log4j2 backend and then reflected
 * here. If the behaviour of this class starts to deviate from that of the log4j2 backend in any
 * significant way, this difference should be called out clearly in the documentation.
 */
final class Log4jLoggerBackend extends LoggerBackend {
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

  @Override
  public void log(LogData logData) {
    // The caller is responsible to call isLoggable() before calling this method to ensure that only
    // messages above the given threshold are logged.
    logger.callAppenders(toLog4jLoggingEvent(logger, logData));
  }

  @Override
  public void handleError(RuntimeException error, LogData badData) {
    logger.callAppenders(toLog4jLoggingEvent(logger, error, badData));
  }
}
