/*
 * Copyright (C) 2020 The Flogger Authors.
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

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingScope;
import com.google.common.flogger.context.Tags;
import com.google.common.flogger.context.LogLevelMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NoOpContextDataProvider extends ContextDataProvider {
  private static final ContextDataProvider NO_OP_INSTANCE = new NoOpContextDataProvider();

  /**
   * Returns a singleton "no op" instance of the context data provider API which logs a warning if
   * used in code which attempts to set context information or modify scopes. This is intended for
   * use by platform implementations in cases where no context is configured.
   */
  public static final ContextDataProvider getInstance() {
    return NO_OP_INSTANCE;
  }

  public static final class NoOpScopedLoggingContext extends ScopedLoggingContext
      implements LoggingScope {
    // Since the ContextDataProvider class is loaded during Platform initialization we must be very
    // careful to avoid any attempt to obtain a logger instance until we can be sure logging config
    // is complete.
    private static final class LazyLogger {
      private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    }
    private final AtomicBoolean haveWarned = new AtomicBoolean();

    private void logWarningOnceOnly() {
      if (haveWarned.compareAndSet(false, true)) {
        LazyLogger.logger
            .atWarning()
            .withStackTrace(StackSize.SMALL)
            .log(
                "Scoped logging contexts are disabled; no context data provider was installed.\n"
                    + "To enable scoped logging contexts in your application, see the "
                    + "site-specific Platform class used to configure logging behaviour.\n"
                    + "Default Platform: com.google.common.flogger.backend.system.DefaultPlatform");
      }
    }

    // The ScopedLoggingContext methods are not themselves called by the core library, so this
    // should not risk any issues with logging during logger initialisation.
    @Override
    public LoggingScope withNewScope() {
      logWarningOnceOnly();
      return this;
    }

    @Override
    public boolean addTags(Tags tags) {
      logWarningOnceOnly();
      return false;
    }

    @Override
    public boolean applyLogLevelMap(LogLevelMap m) {
      logWarningOnceOnly();
      return false;
    }

    @Override
    public void close() {}
  }

  private final ScopedLoggingContext noOpContext = new NoOpScopedLoggingContext();

  @Override
  public ScopedLoggingContext getContextApiSingleton() {
    return noOpContext;
  }

  @Override
  public String toString() {
    return "No-op Provider";
  }
}
