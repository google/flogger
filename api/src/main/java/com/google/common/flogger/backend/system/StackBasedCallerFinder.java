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

package com.google.common.flogger.backend.system;

import com.google.common.flogger.AbstractLogger;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.LogSites;
import com.google.common.flogger.backend.Platform.LogCallerFinder;
import com.google.common.flogger.util.CallerFinder;

/**
 * Default caller finder implementation which should work on all recent Java releases.
 *
 * <p>See class documentation in {@link LogCallerFinder} for important implementation restrictions.
 */
public final class StackBasedCallerFinder extends LogCallerFinder {
  private static final LogCallerFinder INSTANCE = new StackBasedCallerFinder();

  // Called during logging platform initialization; MUST NOT call any code that might log.
  public static LogCallerFinder getInstance() {
    return INSTANCE;
  }

  @Override
  public String findLoggingClass(Class<? extends AbstractLogger<?>> loggerClass) {
    // We can skip at most only 1 method from the analysis, the inferLoggingClass() method itself.
    StackTraceElement caller = CallerFinder.findCallerOf(loggerClass, 1);
    if (caller != null) {
      // This might contain '$' for inner/nested classes, but that's okay.
      return caller.getClassName();
    }
    throw new IllegalStateException("no caller found on the stack for: " + loggerClass.getName());
  }

  @Override
  public LogSite findLogSite(Class<?> loggerApi, int stackFramesToSkip) {
    // Skip an additional stack frame because we create the Throwable inside this method, not at
    // the point that this method was invoked (which allows completely alternate implementations
    // to avoid even constructing the Throwable instance).
    StackTraceElement caller = CallerFinder.findCallerOf(loggerApi, stackFramesToSkip + 1);
    // Returns INVALID if "caller" is null (no caller found for given API class).
    return LogSites.logSiteFrom(caller);
  }

  @Override
  public String toString() {
    return "Default stack-based caller finder";
  }

  private StackBasedCallerFinder() {}
}
