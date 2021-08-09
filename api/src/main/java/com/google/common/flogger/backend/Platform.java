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

package com.google.common.flogger.backend;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.flogger.AbstractLogger;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.Tags;
import com.google.common.flogger.util.RecursionDepth;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

/**
 * Platform abstraction layer required to allow fluent logger implementations to work on differing
 * Java platforms (such as Android or GWT). The {@code Platform} class is responsible for providing
 * any platform specific APIs, including the mechanism by which logging backends are created.
 *
 * <p>To enable an additional logging platform implementation, the class name should be added to
 * the list of available platforms before the default platform (which must always be at the end).
 * Platform implementation classes must subclass {@code Platform} and have a public, no-argument
 * constructor. Platform instances are created on first-use of a fluent logger and platform
 * implementors must take care to avoid cycles during initialization and re-entrant behaviour.
 */
public abstract class Platform {
  // non-final to prevent javac inlining.

  // non-final to prevent javac inlining.

  // non-final to prevent javac inlining.
  @SuppressWarnings("ConstantField")
  private static String DEFAULT_PLATFORM =
      "com.google.common.flogger.backend.system.DefaultPlatform";

  // The first available platform from this list is used. Each platform is defined separately
  // outside of this array so that the IdentifierNameString annotation can be applied to each. This
  // annotation tells Proguard that these strings refer to class names. If Proguard decides to
  // obfuscate those classes, it will also obfuscate these strings, so that reflection can still be
  // used.
  private static final String[] AVAILABLE_PLATFORMS =
      new String[] {
        // The fallback/default platform gives a workable, logging backend.
        DEFAULT_PLATFORM
      };

  // Use the lazy holder idiom here to avoid class loading issues. Loading the Platform sub-class
  // will trigger static initialization of the Platform class first, which would not be possible if
  // the INSTANCE field were a static field in Platform. This means that any errors in platform
  // loading are deferred until the first time one of the Platform's static methods is invoked.
  private static final class LazyHolder {
    private static final Platform INSTANCE = loadFirstAvailablePlatform(AVAILABLE_PLATFORMS);

    private static Platform loadFirstAvailablePlatform(String[] platformClass) {
      Platform platform = null;
      // Try the platform provider first, if it's available.
      try {
        platform = PlatformProvider.getPlatform();
      } catch (NoClassDefFoundError e) {
        // May be an expected error: The PlatformProvider is an optional dependency that can
        // be provided at runtime. Inside Google we use a generator to create the class file for
        // it programmatically, but for third-party use cases the provider could be made available
        // through custom classpath management.
      }
      if (platform != null) {
        return platform;
      }

      StringBuilder errorMessage = new StringBuilder();
      // Try the reflection-based approach as a backup, if the provider isn't available.
      for (String clazz : platformClass) {
        try {
          return (Platform) Class.forName(clazz).getConstructor().newInstance();
        } catch (Throwable e) {
          // Catch errors so if we can't find _any_ implementations, we can report something useful.
          // Unwrap any generic wrapper exceptions for readability here (extend this as needed).
          if (e instanceof InvocationTargetException) {
            e = e.getCause();
          }
          errorMessage.append('\n').append(clazz).append(": ").append(e);
        }
      }
      throw new IllegalStateException(
          errorMessage.insert(0, "No logging platforms found:").toString());
    }
  }

  /**
   * Returns the current depth of recursion for logging in the current thread.
   *
   * <p>This method is intended only for use by logging backends or the core Flogger library and
   * only needs to be called by code which is invoking user code which itself might trigger
   * reentrant logging.
   *
   * <ul>
   *   <li>A value of 1 means that this thread is currently in a normal log statement. This is the
   *   expected state and the caller should behave normally.
   *   <li>A value greater than 1 means that this thread is currently performing reentrant logging,
   *   and the caller may choose to change behaviour depending on the value if there is a risk that
   *   reentrant logging is being caused by the caller's code.
   *   <li>A value of zero means that this thread is not currently logging (though since this method
   *   should only be called as part of a logging library, this is expected to never happen). It
   *   should be ignored.
   * </ul>
   *
   * <p>When the core Flogger library detects the depth exceeding a preset threshold, it may start
   * to modify its behaviour to attempt to mitigate the risk of unbounded reentrant logging. For
   * example, some or all metadata may be removed from log sites, since processing user provided
   * metadata may itself trigger reentrant logging.
   */
  public static int getCurrentRecursionDepth() {
    return RecursionDepth.getCurrentDepth();
  }

  /**
   * API for determining the logging class and log statement sites, return from {@link
   * #getCallerFinder}. This classes is immutable and thread safe.
   *
   * <p>This functionality is not provided directly by the {@code Platform} API because doing so
   * would require several additional levels to be added to the stack before the implementation was
   * reached. This is problematic for Android which has only limited stack analysis. By allowing
   * callers to resolve the implementation early and then call an instance directly (this is not an
   * interface), we reduce the number of elements in the stack before the caller is found.
   *
   * <h2>Essential Implementation Restrictions</h2>
   *
   * Any implementation of this API <em>MUST</em> follow the rules listed below to avoid any risk of
   * re-entrant code calling during logger initialization. Failure to do so risks creating complex,
   * hard to debug, issues with Flogger configuration.
   *
   * <ol>
   *   <li>Implementations <em>MUST NOT</em> attempt any logging in static methods or constructors.
   *   <li>Implementations <em>MUST NOT</em> statically depend on any unknown code.
   *   <li>Implementations <em>MUST NOT</em> depend on any unknown code in constructors.
   * </ol>
   *
   * <p>Note that logging and calling arbitrary unknown code (which might log) are permitted inside
   * the instance methods of this API, since they are not called during platform initialization. The
   * easiest way to achieve this is to simply avoid having any non-trivial static fields or any
   * instance fields at all in the implementation.
   *
   * <p>While this sounds onerous it's not difficult to achieve because this API is a singleton, and
   * can delay any actual work until its methods are called. For example if any additional state is
   * required in the implementation, it can be held via a "lazy holder" to defer initialization.
   */
  public abstract static class LogCallerFinder {
    /**
     * Returns the name of the immediate caller of the given logger class. This is useful when
     * determining the class name with which to create a logger backend.
     *
     * @param loggerClass the class containing the log() methods whose caller we need to find.
     * @return the name of the class that called the specified logger.
     * @throws IllegalStateException if there was no caller of the specified logged passed on the
     *     stack (which may occur if the logger class was invoked directly by JNI).
     */
    public abstract String findLoggingClass(Class<? extends AbstractLogger<?>> loggerClass);

    /**
     * Returns a LogSite found from the current stack trace for the caller of the log() method on
     * the given logging class.
     *
     * @param loggerApi the class containing the log() methods whose caller we need to find.
     * @param stackFramesToSkip the number of method calls which exist on the stack between the
     *     {@code log()} method and the point at which this method is invoked.
     * @return A log site inferred from the stack, or {@link LogSite#INVALID} if no log site can be
     *     determined.
     */
    public abstract LogSite findLogSite(Class<?> loggerApi, int stackFramesToSkip);
  }

  /**
   * Returns the API for obtaining caller information about loggers and logging classes.
   */
  public static LogCallerFinder getCallerFinder() {
    return LazyHolder.INSTANCE.getCallerFinderImpl();
  }

  protected abstract LogCallerFinder getCallerFinderImpl();

  /**
   * Returns a logger backend of the given class name for use by a Fluent Logger. Note that the
   * returned backend need not be unique; one backend could be used by multiple loggers. The given
   * class name must be in the normal dot-separated form (e.g. "com.example.Foo$Bar") rather than
   * the internal binary format (e.g. "com/example/Foo$Bar").
   *
   * @param className the fully-qualified name of the Java class to which the logger is associated.
   *     The logger name is derived from this string in a platform specific way.
   */
  public static LoggerBackend getBackend(String className) {
    return LazyHolder.INSTANCE.getBackendImpl(className);
  }

  protected abstract LoggerBackend getBackendImpl(String className);

  /**
   * Returns the singleton ContextDataProvider from which a ScopedLoggingContext can be obtained.
   * Platform implementations are required to always provide the same instance here, since this can
   * be cached by callers.
   */
  public static ContextDataProvider getContextDataProvider() {
    return LazyHolder.INSTANCE.getContextDataProviderImpl();
  }

  // Provide default implementation here for new API, but Platform implementations are expected to
  // override this (one day it should be possible to make this abstract).
  protected ContextDataProvider getContextDataProviderImpl() {
    return ContextDataProvider.getNoOpProvider();
  }

  /**
   * Returns whether the given logger should have logging forced at the specified level. When
   * logging is forced for a log statement it will be emitted regardless or the normal log level
   * configuration of the logger and ignoring any rate limiting or other filtering.
   * <p>
   * This method is intended to be invoked unconditionally from a fluent logger's
   * {@code at(Level)} method to permit overriding of default logging behavior.
   *
   * @param loggerName the fully qualified logger name (e.g. "com.example.SomeClass")
   * @param level the level of the log statement being invoked
   * @param isEnabled whether the logger is enabled at the given level (i.e. the result of calling
   *     {@code isLoggable()} on the backend instance)
   */
  public static boolean shouldForceLogging(String loggerName, Level level, boolean isEnabled) {
    return getContextDataProvider().shouldForceLogging(loggerName, level, isEnabled);
  }

  /** Returns {@link Tags} from with the current context to be injected into log statements. */
  public static Tags getInjectedTags() {
    return getContextDataProvider().getTags();
  }

  /** Returns {@link Metadata} from with the current context to be injected into log statements. */
  // TODO(dbeaumont): Make this return either an extensible MetadataProcessor or ScopeMetadata.
  public static Metadata getInjectedMetadata() {
    return getContextDataProvider().getMetadata();
  }

  /**
   * Returns the current time from the epoch (00:00 1st Jan, 1970) with nanosecond granularity.
   * This is a non-negative signed 64-bit value, which must be in the range {@code 0 <= timestamp
   * < 2^63}, ensuring that the difference between any two timestamps will always yield a valid
   * signed value.
   * <p>
   * Warning: Not all Platform implementations will be able to deliver nanosecond precision and
   * code should avoid relying on any implied precision.
   */
  public static long getCurrentTimeNanos() {
    return LazyHolder.INSTANCE.getCurrentTimeNanosImpl();
  }

  protected long getCurrentTimeNanosImpl() {
    // Sadly this is the best you can currently do with vanilla Java. In Java9 you have access to
    // nano-second clocks, but Flogger needs to be backwards compatible, so it won't be as simple
    // as just changing this line of code.
    // Overflow will not occur until sometime around 2264, so it's safe not to care for now.
    return MILLISECONDS.toNanos(System.currentTimeMillis());
  }

  /**
   * Returns a human readable string describing the platform and its configuration. This should
   * contain everything a human would need to see to check that the Platform was configured as
   * expected. It should contain the platform name along with any configurable elements
   * (e.g. plugin services) and their settings. It is recommended (though not required) that this
   * string is formatted with one piece of configuration per line in a tabular format, such as:
   * <pre>{@code
   * platform: <human readable name>
   * formatter: com.example.logging.FormatterPlugin
   * formatter.foo: <"foo" settings for the formatter plugin>
   * formatter.bar: <"bar" settings for the formatter plugin>
   * }</pre>
   * It is not required that this string be machine parseable (though it should be stable).
   */
  public static String getConfigInfo() {
    return LazyHolder.INSTANCE.getConfigInfoImpl();
  }

  protected abstract String getConfigInfoImpl();
}
