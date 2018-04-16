/*
 * Copyright (C) 2017 The Flogger Authors.
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

package com.google.common.flogger.backend.system;

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.backend.Platform.LogCallerFinder;
import javax.annotation.Nullable;

/** Mutable configuration holder passed to the {@link DefaultPlatform#configure} method. */
public final class Configuration {
  private BackendFactory backendFactory = null;
  private Clock clock = null;
  private LoggingContext context = null;
  private LogCallerFinder callerFinder = null;

  Configuration() {}

  /**
   * Sets the factory implementation with which logger backends are generated. The default
   * factory creates instances of {@link SimpleLoggerBackend}.
   */
  public void setBackendFactory(BackendFactory backendFactory) {
    this.backendFactory = checkNotNull(backendFactory, "backend factory");
  }

  @Nullable
  BackendFactory getBackendFactory() {
    return backendFactory;
  }

  /**
   * Sets the clock to be used to obtain timestamps for log statements, overriding any
   * previously set value. The default clock implementation will attempt to use the most precise
   * mechanism available in the core Java runtime, but alternate clocks with better precision may
   * be injected by this method.
   */
  public void setClock(Clock clock) {
    this.clock = checkNotNull(clock, "clock");
  }

  @Nullable
  Clock getClock() {
    return clock;
  }

  /**
   * Sets the logging context used to obtain additional injected data for log statements,
   * overriding. any previously set value. There is no default logging context on the default
   * platform.
   */
  public void setLoggingContext(LoggingContext context) {
    this.context = checkNotNull(context, "logging context");
  }

  @Nullable
  LoggingContext getLoggingContext() {
    return context;
  }

  /**
   * Sets the caller finder used to determine logger names and log site information. The default
   * value for this property should be sufficient for most use cases and it's unlikely to need
   * to be overridden other than in tests.
   */
  public void setCallerFinder(LogCallerFinder callerFinder) {
    this.callerFinder = checkNotNull(callerFinder, "caller finder");
  }

  @Nullable
  LogCallerFinder getCallerFinder() {
    return callerFinder;
  }
}
