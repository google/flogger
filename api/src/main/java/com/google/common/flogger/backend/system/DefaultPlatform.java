/*
 * Copyright (C) 2016 The Flogger Authors.
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.flogger.AbstractLogger;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.LoggingApi;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.Tags;
import com.google.common.flogger.util.CallerFinder;
import com.google.common.flogger.util.StackBasedLogSite;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The default fluent logger platform for a server-side Java environment. The default platform
 * implements the following behavior:
 * <ul>
 *   <li>It generates {@code SimpleLoggerBackend} logger backends.
 *   <li>It uses a default clock implementation (potential only millisecond precision).
 *   <li>It does not provide support for injected additional metadata into log statements.
 *   <li>It determines call site information via stack analysis (this is unlikely to ever need to
 *       be overridden).
 * </ul>
 * <p>This class is designed to allow subclasses to override default behaviour via the
 * {@link #configure(Configuration)} method.
 */
public class DefaultPlatform extends Platform {
  /** Default factory for creating logger backends. */
  private static final class SimpleBackendFactory extends BackendFactory {
    @Override
    public LoggerBackend create(String loggingClass) {
      // TODO(b/27920233): Strip inner/nested classes when deriving logger name.
      Logger logger = Logger.getLogger(loggingClass.replace('$', '.'));
      return new SimpleLoggerBackend(logger);
    }

    @Override
    public String toString() {
      return "Default logger backend factory";
    }
  }

  /** Default caller finder implementation which should work on all recent Java releases. */
  private static final class StackBasedCallerFinder extends LogCallerFinder {
    @Override
    public String findLoggingClass(Class<? extends AbstractLogger<?>> loggerClass) {
      // We can skip at most only 1 method from the analysis, the inferLoggingClass() method itself.
      StackTraceElement caller = CallerFinder.findCallerOf(loggerClass, new Throwable(), 1);
      if (caller != null) {
        // This might contain '$' for inner/nested classes, but that's okay.
        return caller.getClassName();
      }
      throw new IllegalStateException("no caller found on the stack for: " + loggerClass.getName());
    }

    @Override
    public LogSite findLogSite(Class<? extends LoggingApi<?>> loggerApi, int stackFramesToSkip) {
      // Skip an additional stack frame because we create the Throwable inside this method, not at
      // the point that this method was invoked (which allows completely alternate implementations
      // to avoid even constructing the Throwable instance).
      StackTraceElement caller =
          CallerFinder.findCallerOf(loggerApi, new Throwable(), stackFramesToSkip + 1);
      return caller != null ? new StackBasedLogSite(caller) : LogSite.INVALID;
    }

    @Override
    public String toString() {
      return "Default stack-based caller finder";
    }
  }

  /** Default millisecond precision clock. */
  private static final class SystemClock extends Clock {
    @Override
    public long getCurrentTimeNanos() {
      return MILLISECONDS.toNanos(System.currentTimeMillis());
    }

    @Override
    public String toString() {
      return "Default millisecond precision clock";
    }
  }

  /** Empty trace context implementation. */
  private static final class EmptyContext extends LoggingContext {
    @Override
    public boolean shouldForceLogging(String loggerName, Level level, boolean isEnabled) {
      // Never add any debug or logging here (see LoggingContext for details).
      return false;
    }

    @Override
    public Tags getTags() {
      return Tags.empty();
    }

    @Override
    public String toString() {
      return "Empty logging context";
    }
  }

  private final BackendFactory backendFactory;
  private final LogCallerFinder callerFinder;
  private final Clock clock;
  private final LoggingContext context;
  private final String configInfo;

  /**
   * Constructs a logger platform instance which is configured by calling the
   * {@link #configure(Configuration)} method which can be overridden by subclasses.
   */
  public DefaultPlatform() {
    Configuration config = new Configuration();
    // Invoke subclass specific configuration.
    configure(config);
    // Only instantiate default "plugins" if none were supplied by the subclass.
    this.backendFactory = config.getBackendFactory() != null
        ? config.getBackendFactory() : new SimpleBackendFactory();
    this.callerFinder = config.getCallerFinder() != null
        ? config.getCallerFinder() : new StackBasedCallerFinder();
    this.clock = config.getClock() != null
        ? config.getClock() : new SystemClock();
    this.context = config.getLoggingContext() != null
        ? config.getLoggingContext() : new EmptyContext();
    this.configInfo = formatConfigInfo();
  }

  /**
   * Configures this platform by setting any required aspects of platform behavior. Subclasses
   * which override this method to apply custom configuration are expected to always invoke
   * {@code super.configure(config)} at the start of their method.
   */
  protected void configure(Configuration config) {
    // Do nothing since while a subclass should always call super.configure(...) first, we can't
    // rely on this, so we add the base defaults prior to calling this method at all.
  }

  @Override
  protected final LogCallerFinder getCallerFinderImpl() {
    return callerFinder;
  }

  @Override
  protected final LoggerBackend getBackendImpl(String className) {
    return backendFactory.create(className);
  }

  @Override
  protected final boolean shouldForceLoggingImpl(String loggerName, Level level, boolean isEnabled) {
    return context.shouldForceLogging(loggerName, level, isEnabled);
  }

  @Override
  protected final Tags getInjectedTagsImpl() {
    return context.getTags();
  }

  @Override
  protected final long getCurrentTimeNanosImpl() {
    return clock.getCurrentTimeNanos();
  }

  @Override
  protected final String getConfigInfoImpl() {
    return configInfo;
  }

  private String formatConfigInfo() {
    StringBuilder out = new StringBuilder();
    out.append("Platform: ").append(getClass().getName()).append("\n");
    out.append("BackendFactory: \"").append(backendFactory).append("\"\n");
    out.append("Clock: \"").append(clock).append("\"\n");
    out.append("LoggingContext: \"").append(context).append("\"\n");
    out.append("LogCallerFinder: \"").append(callerFinder).append("\"\n");
    return out.toString();
  }
}
