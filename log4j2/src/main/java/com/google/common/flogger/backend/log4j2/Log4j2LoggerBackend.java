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

import static com.google.common.flogger.backend.log4j2.Log4j2LogEventUtil.toLog4jLevel;
import static com.google.common.flogger.backend.log4j2.Log4j2LogEventUtil.toLog4jLogEvent;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import org.apache.logging.log4j.core.Logger;

/**
 * A logging backend that uses log4j2 to output log statements.
 *
 * <p>Note: Any changes in this code should, as far as possible, be reflected in the equivalently
 * named log4j implementation. If the behaviour of this class starts to deviate from that of the
 * log4j backend in any significant way, this difference should be called out clearly in the
 * documentation.
 */
final class Log4j2LoggerBackend extends LoggerBackend {
  private final Logger logger;

  // VisibleForTesting
  Log4j2LoggerBackend(Logger logger) {
    this.logger = logger;
  }

  @Override
  public String getLoggerName() {
    // Logger#getName() returns exactly the name that we used to create the Logger in
    // Log4jBackendFactory.
    return logger.getName();
  }

  @Override
  public boolean isLoggable(java.util.logging.Level level) {
    return logger.isEnabled(toLog4jLevel(level));
  }

  @Override
  public void log(LogData logData) {
    // The caller is responsible to call isLoggable() before calling this method to ensure that only
    // messages above the given threshold are logged.
    logger.get().log(toLog4jLogEvent(logger.getName(), logData));
  }

  @Override
  public void handleError(RuntimeException error, LogData badData) {
    logger.get().log(toLog4jLogEvent(logger.getName(), error, badData));
  }
}
