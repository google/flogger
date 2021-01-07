/*
 * Copyright (C) 2012 The Flogger Authors.
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

package com.google.common.flogger;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.LoggingException;
import com.google.errorprone.annotations.CheckReturnValue;

import java.util.logging.Level;

import static com.google.common.flogger.util.Checks.checkNotNull;

/**
 * Base class for the fluent logger API. This class is a factory for instances of a fluent logging
 * API, used to build log statements via method chaining.
 */
@CheckReturnValue
public abstract class AbstractLogger {
  private final LoggerBackend backend;

  /**
   * Constructs a new logger for the specified backend.
   *
   * @param backend the logger backend which ultimately writes the log statements out.
   */
  protected AbstractLogger(LoggerBackend backend) {
    this.backend = checkNotNull(backend, "backend");
  }

  // ---- HELPER METHODS (useful during sub-class initialization) ----

  /**
   * Returns the non-null name of this logger (Flogger does not currently support anonymous
   * loggers).
   */
  // IMPORTANT: Flogger does not currently support the idea of an anonymous logger instance
  // (but probably should). The issue here is that in order to allow the FluentLogger instance
  // and the LoggerConfig instance to share the same underlying logger, while allowing the
  // backend API to be flexible enough _not_ to admit the existence of the JDK logger, we will
  // need to push the LoggerConfig API down into the backend and expose it from there.
  // See b/14878562
  // TODO(dbeaumont): Make anonymous loggers work with the config() method and the LoggerConfig API.
  protected String getName() {
    return backend.getLoggerName();
  }

  // ---- IMPLEMENTATION DETAIL (only visible to the base logging context) ----

  /**
   * Returns the logging backend (not visible to logger subclasses to discourage tightly coupled
   * implementations).
   */
  final LoggerBackend getBackend() {
    return backend;
  }

  /** Invokes the logging backend to write a log statement. */
  final void write(LogData data) {
    checkNotNull(data, "data");
    try {
      backend.log(data);
    } catch (RuntimeException error) {
      try {
        backend.handleError(error, data);
      } catch (LoggingException allowed) {
        // Bypass the catch-all if the exception is deliberately created during error handling.
        throw allowed;
      } catch (RuntimeException wtf) {
        System.err.println("logging error: " + wtf.getMessage());
        wtf.printStackTrace(System.err);
      }
    }
  }
}
