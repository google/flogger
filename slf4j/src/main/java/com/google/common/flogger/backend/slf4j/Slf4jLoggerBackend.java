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

  private static final MappedLevel OFF_LEVEL = new MappedLevel(new MappedLevel.Predicate() {

    @Override
    public boolean test(Logger logger) {
      return false;
    }
  }, new MappedLevel.LogAdapter() {
    @Override
    public void log(Logger logger, String message, Throwable thrown) {
      // no-op
    }
  });

  private static final MappedLevel TRACE_LEVEL = new MappedLevel(new MappedLevel.Predicate() {

    @Override
    public boolean test(Logger logger) {
      return logger.isTraceEnabled();
    }
  }, new MappedLevel.LogAdapter() {
    @Override
    public void log(Logger logger, String message, Throwable thrown) {
      logger.trace(message, thrown);
    }
  });

  private static final MappedLevel DEBUG_LEVEL = new MappedLevel(new MappedLevel.Predicate() {

    @Override
    public boolean test(Logger logger) {
      return logger.isDebugEnabled();
    }
  }, new MappedLevel.LogAdapter() {
    @Override
    public void log(Logger logger, String message, Throwable thrown) {
      logger.debug(message, thrown);
    }
  });
  private static final MappedLevel INFO_LEVEL = new MappedLevel(new MappedLevel.Predicate() {

    @Override
    public boolean test(Logger logger) {
      return logger.isInfoEnabled();
    }
  }, new MappedLevel.LogAdapter() {
    @Override
    public void log(Logger logger, String message, Throwable thrown) {
      logger.info(message, thrown);
    }
  });
  private static final MappedLevel WARN_LEVEL = new MappedLevel(new MappedLevel.Predicate() {

    @Override
    public boolean test(Logger logger) {
      return logger.isWarnEnabled();
    }
  }, new MappedLevel.LogAdapter() {
    @Override
    public void log(Logger logger, String message, Throwable thrown) {
      logger.warn(message, thrown);
    }
  });
  private static final MappedLevel ERROR_LEVEL = new MappedLevel(new MappedLevel.Predicate() {

    @Override
    public boolean test(Logger logger) {
      return logger.isErrorEnabled();
    }
  }, new MappedLevel.LogAdapter() {
    @Override
    public void log(Logger logger, String message, Throwable thrown) {
      logger.error(message, thrown);
    }
  });

  Slf4jLoggerBackend(Logger logger) {
    if (logger == null) {
      throw new NullPointerException("logger is required");
    }
    this.logger = logger;
  }

  private static MappedLevel mapLevel(Level level) {
    int requestedLevel = level.intValue();
    if( requestedLevel < Level.FINE.intValue() ) {
      return TRACE_LEVEL;
    }

    if( requestedLevel < Level.INFO.intValue() ) {
      return DEBUG_LEVEL;
    }

    if( requestedLevel < Level.WARNING.intValue() ) {
      return INFO_LEVEL;
    }

    if( requestedLevel < Level.SEVERE.intValue() ) {
      return WARN_LEVEL;
    }

    if( requestedLevel < Level.OFF.intValue() ) {
      return ERROR_LEVEL;
    }

    return OFF_LEVEL;
  }

  @Override
  public String getLoggerName() {
    return logger.getName();
  }

  @Override
  public boolean isLoggable(Level level) {
    return mapLevel(level).isLoggable(logger);
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
    mapLevel(level).log(logger, message, thrown);
  }

  private static class MappedLevel {

    private final Predicate loggingEnabledPredicate;
    private final LogAdapter logAdapter;

    private MappedLevel(Predicate loggingEnabledPredicate, LogAdapter logAdapter) {
      if (loggingEnabledPredicate == null) {
        throw new NullPointerException("loggingEnabledPredicate required");
      }
      if (logAdapter == null) {
        throw new NullPointerException("logAdapter required");
      }
      this.loggingEnabledPredicate = loggingEnabledPredicate;
      this.logAdapter = logAdapter;
    }

    boolean isLoggable(Logger logger) {
      return loggingEnabledPredicate.test(logger);
    }

    void log(Logger logger, String message, Throwable thrown) {
      logAdapter.log(logger, message, thrown);
    }

    interface LogAdapter {

      void log(Logger logger, String message, Throwable thrown);
    }

    interface Predicate {

      boolean test(Logger logger);
    }
  }

}
