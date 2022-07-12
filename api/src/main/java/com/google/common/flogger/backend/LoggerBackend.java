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

package com.google.common.flogger.backend;

import java.util.logging.Level;

/**
 * Interface for all logger backends.
 *
 * <p>
 *
 * <h2>Implementation Notes:</h2>
 *
 * Often each {@link com.google.common.flogger.AbstractLogger} instance will be instantiated with a
 * new logger backend (to permit per-class logging behavior). Because of this it is important that
 * LoggerBackends have as little per-instance state as possible.
 *
 * <p>It is also essential that no implementation of {@code LoggerBackend} ever holds onto user
 * supplied objects (especially log statement arguments) after the {@code log()} or {@code
 * handleError()} methods to which they were passed have exited.
 *
 * <p>This means that <em>ALL</em> formatting or serialization of log statement arguments or
 * metadata values <em>MUST</em> be completed inside the log method itself. If the backend needs to
 * perform asynchronous I/O operations it can do so by constructing a serialized form of the {@link
 * LogData} instance and enqueing that for processing.
 *
 * <p>Note also that this restriction is <em>NOT</em> purely about mutable arguments (which could
 * change before formatting occurs and produce incorrect output), but also stops log statements from
 * changing the lifetime of arbitrary user arguments, which can cause "use after close" bugs and
 * other garbage collector issues.
 */
public abstract class LoggerBackend {
  /**
   * Returns the logger name (which is usually a canonicalized class name) or {@code null} if not
   * given.
   */
  public abstract String getLoggerName();

  /**
   * Returns whether logging is enabled for the given level for this backend. Different backends may
   * return different values depending on the class with which they are associated.
   */
  public abstract boolean isLoggable(Level lvl);

  /**
   * Outputs the log statement represented by the given {@link LogData} instance.
   *
   * @param data user and logger supplied data to be rendered in a backend specific way. References
   *     to {@code data} must not be held after the {@link log} invocation returns.
   */
  public abstract void log(LogData data);

  /**
   * Handles an error in a log statement. Errors passed into this method are expected to have only
   * three distinct causes:
   *
   * <ol>
   *   <li>Bad format strings in log messages (e.g. {@code "foo=%Q"}. These will always be instances
   *       of {@link com.google.common.flogger.parser.ParseException ParseException} and contain
   *       human readable error messages describing the problem.
   *   <li>A backend optionally choosing not to handle errors from user code during formatting. This
   *       is not recommended (see below) but may be useful in testing or debugging.
   *   <li>Runtime errors in the backend itself.
   * </ol>
   *
   * <p>It is recommended that backend implementations avoid propagating exceptions in user code
   * (e.g. calls to {@code toString()}), as the nature of logging means that log statements are
   * often only enabled when debugging. If errors were propagated up into user code, enabling
   * logging to look for the cause of one issue could trigger previously unknown bugs, which could
   * then seriously hinder debugging the original issue.
   *
   * <p>Typically a backend would handle an error by logging an alternative representation of the
   * "bad" log data, being careful not to allow any more exceptions to occur. If a backend chooses
   * to propagate an error (e.g. when testing or debugging) it must wrap it in {@link
   * LoggingException} to avoid it being re-caught.
   *
   * @param error the exception throw when {@code badData} was initially logged.
   * @param badData the original {@code LogData} instance which caused an error. It is not expected
   *     that simply trying to log this again will succeed and error handlers must be careful in how
   *     they handle this instance, its arguments and metadata. References to {@code badData} must
   *     not be held after the {@link handleError} invocation returns.
   * @throws LoggingException to indicate an error which should be propagated into user code.
   */
  public abstract void handleError(RuntimeException error, LogData badData);
}
