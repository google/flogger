/*
 * Copyright (C) 2013 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.checkArgument;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import java.util.logging.Level;

/**
 * The default Google specific implementation of {@link AbstractLogger} which extends the core
 * {@link LoggingApi} to add Google specific functionality.
 */
public final class GoogleLogger extends AbstractLogger<GoogleLogger.Api> {
  /** See {@link GoogleLoggingApi}. */
  public interface Api extends GoogleLoggingApi<Api> {}

  // No-op implementation of the non-wildcard API.
  private static final class NoOp extends GoogleLoggingApi.NoOp<Api> implements Api {}

  // Singleton instance of the no-op API. This variable is purposefully declared as an instance of
  // the NoOp type instead of the Api type. This helps ProGuard optimization recognize the type of
  // this field more easily. This allows ProGuard to strip away low-level logs in Android apps in
  // fewer optimization passes. Do not change this to 'Api', or any less specific type.
  private static final NoOp NO_OP = new NoOp();

  /** Returns a new Google specific logger instance using printf style message formatting. */
  public static GoogleLogger forEnclosingClass() {
    // NOTE: It is _vital_ that the call to "caller finder" is made directly inside the static
    // factory method. See getCallerFinder() for more information.
    String loggingClass = Platform.getCallerFinder().findLoggingClass(GoogleLogger.class);
    return new GoogleLogger(Platform.getBackend(loggingClass));
  }

  /**
   * Returns a new Google specific logger instance for the given class, using printf
   * style message formatting.
   *
   * @deprecated prefer forEnclosingClass(); this method exists only to support
   * compile-time log site injection.
   */
  @Deprecated
  public static GoogleLogger forInjectedClassName(String className) {
    checkArgument(!className.isEmpty(), "injected class name is empty");
    // The injected class name is in binary form (e.g. java/util/Map$Entry) to re-use
    // constant pool entries.
    return new GoogleLogger(Platform.getBackend(className.replace('/', '.')));
  }

  // VisibleForTesting
  GoogleLogger(LoggerBackend loggerBackend) {
    super(loggerBackend);
  }

  @Override
  public Api at(Level level) {
    // Important: This code is the "hottest" code path in Flogger. It is called for all log
    // statements or log level checks reached by user code. The overwhelming number of callers to
    // this method will be non-loggable and return the singleton "no op" API. Profiling of server
    // side applications shows that approximately 80% of the total CPU usage associated with this
    // method is used for cases where logging will not occur, so any changes must be optimized for
    // that case. In-particular no allocation should ever occur for "disabled" log statements.
    //
    // In systems where log statements are stripped from code at build time, this obviously
    // dramatically affects the ratio of enabled to disabled log statements, but in those cases,
    // the cost of this method is trivial compared to the work of actually logging.
    //
    // As a general optimization, we have worked to avoid exceeding the 35-bytecode heuristic for
    // inlining decisions. However, it's not clear that this helps performance. We could further
    // reduce bytecodes by switching from || to | (which may affect performance in other ways).
    // Conceivably we could do even better by writing our own bytecode to replace load and store
    // operations with stack management. Perhaps all this would help to enable multiple levels of
    // the call tree to be inlined?
    boolean isLoggable = isLoggable(level);
    boolean isForced = Platform.shouldForceLogging(getName(), level, isLoggable);
    if (isLoggable || isForced) {
      return newContext(level, isForced);
    }
    return NO_OP;
  }

  /*
   * Extracted to a separate method to keep at(Level) from exceeding the 35-bytecode heuristic for
   * inlining decisions. But see discussion in at(Level).
   */
  private Context newContext(Level level, boolean isForced) {
    return new Context(level, isForced);
  }

  /** Logging context implementing the fully specified API for this logger. */
  private final class Context extends GoogleLogContext<GoogleLogger, Api> implements Api {
    // Non-private so that javac doesn't have to generate a bridge constructor.
    Context(Level level, boolean isForced) {
      super(level, isForced);
    }

    @Override
    protected GoogleLogger getLogger() {
      return GoogleLogger.this;
    }

    @Override
    protected Api api() {
      return this;
    }

    @Override
    protected Api noOp() {
      return NO_OP;
    }
  }
}
