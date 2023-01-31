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

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.parser.DefaultPrintfMessageParser;
import com.google.common.flogger.parser.MessageParser;
import java.util.logging.Level;

/**
 * The default implementation of {@link AbstractLogger} which returns the basic {@link LoggingApi}
 * and uses the default parser and system configured backend.
 * <p>
 * Note that when extending the logging API or specifying a new parser, you will need to create a
 * new logger class (rather than extending this one). Unlike the {@link LogContext} class,
 * which must be extended in order to modify the logging API, this class is not generified and thus
 * cannot be modified to produce a different logging API.
 * <p>
 * The choice to prevent direct extension of loggers was made deliberately to ensure that users of
 * a specific logger implementation always get the same behavior.
 */
public final class FluentLogger extends AbstractLogger<FluentLogger.Api> {
  /**
   * The non-wildcard, fully specified, logging API for this logger. Fluent logger implementations
   * should specify a non-wildcard API like this with which to generify the abstract logger.
   * <p>
   * It is possible to add methods to this logger-specific API directly, but it is recommended that
   * a separate top-level API and LogContext is created, allowing it to be shared by other
   * implementations.
   */
  public interface Api extends LoggingApi<Api> {}

  /**
   * The non-wildcard, fully specified, no-op API implementation. This is required to provide a
   * no-op implementation whose type is compatible with this logger's API.
   */
  private static final class NoOp extends LoggingApi.NoOp<Api> implements Api {}

  // Singleton instance of the no-op API. This variable is purposefully declared as an instance of
  // the NoOp type instead of the Api type. This helps ProGuard optimization recognize the type of
  // this field more easily. This allows ProGuard to strip away low-level logs in Android apps in
  // fewer optimization passes. Do not change this to 'Api', or any less specific type.
  // VisibleForTesting
  static final NoOp NO_OP = new NoOp();

  /**
   * Returns a new logger instance which parses log messages using printf format for the enclosing
   * class using the system default logging backend.
   */
  public static FluentLogger forEnclosingClass() {
    // NOTE: It is _vital_ that the call to "caller finder" is made directly inside the static
    // factory method. See getCallerFinder() for more information.
    String loggingClass = Platform.getCallerFinder().findLoggingClass(FluentLogger.class);
    return new FluentLogger(Platform.getBackend(loggingClass));
  }

  // Visible for testing.
  FluentLogger(LoggerBackend backend) {
    super(backend);
  }

  @Override
  public Api at(Level level) {
    boolean isLoggable = isLoggable(level);
    boolean isForced = Platform.shouldForceLogging(getName(), level, isLoggable);
    return (isLoggable || isForced) ? new Context(level, isForced) : NO_OP;
  }

  /** Logging context implementing the fully specified API for this logger. */
  // VisibleForTesting
  final class Context extends LogContext<FluentLogger, Api> implements Api {
    private Context(Level level, boolean isForced) {
      super(level, isForced);
    }

    @Override
    protected FluentLogger getLogger() {
      return FluentLogger.this;
    }

    @Override
    protected Api api() {
      return this;
    }

    @Override
    protected Api noOp() {
      return NO_OP;
    }

    @Override
    protected MessageParser getMessageParser() {
      return DefaultPrintfMessageParser.getInstance();
    }
  }
}
