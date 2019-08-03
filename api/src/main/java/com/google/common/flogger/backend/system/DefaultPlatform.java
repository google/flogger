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

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.Tags;
import com.google.common.flogger.util.Checks;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * The default fluent logger platform for a server-side Java environment. The default platform
 * implements the following behavior:
 * <ul>
 *   <li>It generates {@code SimpleLoggerBackend} logger backends.
 *   <li>It uses a default clock implementation (only millisecond precision until Java 8).
 *   <li>It does not provide support for injecting additional metadata into log statements.
 *   <li>It determines call site information via stack analysis.
 * </ul>
 *
 * <p>This class is designed to allow configuration via system properties. Each aspect of the
 * platform is configured by providing the name of a static method, in the form
 * {@code "<package>.<class>#<method>"}, which returns an instance of the appropriate type.
 *
 * The namespace for system properties is:
 * <ul>
 *   <li>{@code flogger.backend_factory}: Provides an instance of
 *       {@code com.google.common.flogger.backend.system.BackendFactory}.
 *   <li>{@code flogger.logging_context}: Provides an instance of
 *       {@code com.google.common.flogger.backend.system.LoggingContext}.
 *   <li>{@code flogger.clock}: Provides an instance of
 *       {@code com.google.common.flogger.backend.system.Clock}.
 * </ul>
 */
// Non-final for testing.
public class DefaultPlatform extends Platform {
  private static final String BACKEND_FACTORY = "backend_factory";
  private static final String LOGGING_CONTEXT = "logging_context";
  private static final String CLOCK = "clock";

  private final BackendFactory backendFactory;
  private final LoggingContext context;
  private final Clock clock;
  private final LogCallerFinder callerFinder;

  public DefaultPlatform() {
    BackendFactory factory = resolveAttribute(BACKEND_FACTORY, BackendFactory.class);
    this.backendFactory = (factory != null) ? factory : SimpleBackendFactory.getInstance();
    LoggingContext context = resolveAttribute(LOGGING_CONTEXT, LoggingContext.class);
    this.context = (context != null) ? context : EmptyLoggingContext.getInstance();
    Clock clock = resolveAttribute(CLOCK, Clock.class);
    this.clock = (clock != null) ? clock : SystemClock.getInstance();
    // TODO(dbeaumont): Figure out how to handle StackWalker when it becomes available (Java9).
    this.callerFinder = StackBasedCallerFinder.getInstance();
  }

  // Visible for testing
  DefaultPlatform(
      BackendFactory factory, LoggingContext context, Clock clock, LogCallerFinder callerFinder) {
    this.backendFactory = factory;
    this.context = context;
    this.clock = clock;
    this.callerFinder = callerFinder;
  }

  @Override
  protected LogCallerFinder getCallerFinderImpl() {
    return callerFinder;
  }

  @Override
  protected LoggerBackend getBackendImpl(String className) {
    return backendFactory.create(className);
  }

  @Override
  protected boolean shouldForceLoggingImpl(String loggerName, Level level, boolean isEnabled) {
    return context.shouldForceLogging(loggerName, level, isEnabled);
  }

  @Override
  protected Level getForceLoggingLevelOnLevelUnableImpl() {
    return context.getForceLoggingLevelOnLevelUnable();
  }

  @Override
  protected Tags getInjectedTagsImpl() {
    return context.getTags();
  }

  @Override
  protected long getCurrentTimeNanosImpl() {
    return clock.getCurrentTimeNanos();
  }

  @Override
  protected String getConfigInfoImpl() {
    return "Platform: " + getClass().getName() + "\n"
        + "BackendFactory: " + backendFactory + "\n"
        + "Clock: " + clock + "\n"
        + "LoggingContext: " + context + "\n"
        + "LogCallerFinder: " + callerFinder + "\n";
  }

  /**
   * Helper to call a static no-arg getter to obtain an instance of a specified type. This is used
   * for platform aspects which are optional, but are expected to have a singleton available.
   *
   * @return the return value of the specified static no-argument method, or null if the method
   *     cannot be called or the returned value is of the wrong type.
   */
  @Nullable
  private static <T> T resolveAttribute(String attributeName, Class<T> type) {
    String getter = readProperty(attributeName);
    if (getter == null) {
      return null;
    }
    int idx = getter.indexOf('#');
    if (idx <= 0 || idx == getter.length() - 1) {
      error("invalid getter (expected <class>#<method>): %s\n", getter);
      return null;
    }
    return callStaticMethod(getter.substring(0, idx), getter.substring(idx + 1), type);
  }

  private static String readProperty(String attributeName) {
    Checks.checkNotNull(attributeName, "attribute name");
    String propertyName = "flogger." + attributeName;
    try {
      return System.getProperty(propertyName);
    } catch (SecurityException e) {
      error("cannot read property name %s: %s", propertyName, e);
    }
    return null;
  }

  private static <T> T callStaticMethod(String className, String methodName, Class<T> type) {
    try {
      return type.cast(Class.forName(className).getMethod(methodName).invoke(null));
    } catch (ClassNotFoundException e) {
      // Expected if an optional aspect is not being used (no error).
    } catch (ClassCastException e) {
      error("cannot cast result of calling '%s#%s' to '%s': %s\n",
          className, methodName, type.getName(), e);
    } catch (Exception e) {
      // Catches SecurityException *and* ReflexiveOperationException (which doesn't exist in 1.6).
      error("cannot call expected no-argument static method '%s#%s': %s\n",
          className, methodName, e);
    }
    return null;
  }

  private static void error(String msg, Object... args) {
    System.err.println(DefaultPlatform.class + ": " + String.format(msg, args));
  }
}
