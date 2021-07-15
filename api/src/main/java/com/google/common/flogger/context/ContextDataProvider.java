/*
 * Copyright (C) 2019 The Flogger Authors.
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

package com.google.common.flogger.context;

import com.google.common.flogger.LoggingScope;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.Platform;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * An API for injecting scoped metadata for log statements (either globally or on a per-request
 * basis). Thiis class is not a public API and should never need to be invoked directly by
 * application code.
 *
 * <p>Note that since this class (and any installed implementation sub-class) is loaded when the
 * logging platform is loaded, care must be taken to avoid cyclic references during static
 * initialisation. This means that no static fields or static initialization can reference fluent
 * loggers or the logging platform (either directly or indirectly).
 *
 * <h2>This is a service type</h2>
 *
 * <p>This type is considered a <i>service type</i> and implemenations may be loaded from the
 * classpath via {@link java.util.ServiceLoader} provided the proper service metadata is included in
 * the jar file containing the implementation. When creating an implementation of this class, you
 * can provide serivce metadata (and thereby allow users to get your implementation just by
 * including your jar file) by either manually including a {@code
 * META-INF/services/com.google.common.flogger.context.ContextDataProvider} file containing the name
 * of your implementation class or by annotating your implementation class using <a
 * href="https://github.com/google/auto/tree/master/service">
 * {@code @AutoService(ContextDataProvider.class)}</a>. See the documentation of both {@link
 * java.util.ServiceLoader} and {@link com.google.common.flogger.backend.system.DefaultPlatform} for
 * more information.
 */
public abstract class ContextDataProvider {
  /**
   * Returns the singleton instance of the context data provider for use by logging platform
   * implementations. This method should not be called by general application code, and the {@code
   * ContextDataProvider} class should never need to be used directly outside of fluent logger
   * platform implementations.
   */
  public static ContextDataProvider getInstance() {
    return Platform.getContextDataProvider();
  }

  /**
   * Returns the singleton no-op context data provider, which can be used by platform
   * implementations which don't support {@code ScopedLoggingContext} for some reason. The returned
   * provider has no effect and returns empty/default data in all cases.
   *
   * <p>In general this method should never need to be called outside the core Flogger libraries.
   */
  public static ContextDataProvider getNoOpProvider() {
    return NoOpContextDataProvider.getNoOpInstance();
  }

  /**
   * Returns the context API with which users can create and modify the state of logging contexts
   * within an application. This method should be overridden by subclasses to provide the specific
   * implementation of the API.
   *
   * <p>This method should never be called directly (other than in tests) and users should always go
   * via {@link ScopedLoggingContext#getInstance}, without needing to reference this class at all.
   *
   * <p>If an implementation wishes to allow logging from the context API class, that class must be
   * lazily loaded when this method is called (e.g. using a "lazy holder"). Failure to do so is
   * likely to result in errors during the initialization of the logging platform classes.
   */
  public abstract ScopedLoggingContext getContextApiSingleton();

  /**
   * Returns whether the given logger should have logging forced at the specified level. When
   * logging is forced for a log statement it will be emitted regardless or the normal log level
   * configuration of the logger and ignoring any rate limiting or other filtering.
   *
   * <p>Implementations which do not support forced logging should not override this method; the
   * default implementation returns {@code false}.
   *
   * <p>{@code loggerName} can be used to look up specific configuration, such as log level, for the
   * logger, to decide if a log statement should be forced. This information might vary depending on
   * the context in which this call is made, so the result should not be cached.
   *
   * <p>{@code isEnabledByLevel} indicates that the log statement is enabled according to its log
   * level, but a {@code true} value does not necessarily indicate that logging will occur, due to
   * rate limiting or other conditional logging mechanisms. To bypass conditional logging and ensure
   * that an enabled log statement will be emitted, this method should return {@code true} if {@code
   * isEnabledByLevel} was {@code true}.
   *
   * <p>WARNING: This method MUST complete quickly and without allocating any memory. It is invoked
   * for every log statement regardless of logging configuration, so any implementation must go to
   * every possible length to be efficient.
   *
   * @param loggerName the fully qualified logger name (e.g. "com.example.SomeClass")
   * @param level the level of the log statement being invoked
   * @param isEnabledByLevel whether the logger is enabled at the given level
   */
  public boolean shouldForceLogging(String loggerName, Level level, boolean isEnabledByLevel) {
    return false;
  }

  /**
   * Returns a set of tags to be added to a log statement. These tags can be used to provide
   * additional contextual metadata to log statements (e.g. request IDs).
   *
   * <p>Implementations which do not support scoped {@link Tags} should not override this method;
   * the default implementation returns {@code Tags.empty()}.
   */
  public Tags getTags() {
    return Tags.empty();
  }

  /**
   * Returns metadata to be applied to a log statement. Scoped metadata can be used to provide
   * structured data to log statements or control logging behaviour (in conjunction with a custom
   * logger backend).
   *
   * <p>Implementations which do not support scoped {@link Metadata} should not override this
   * method; the default implementation returns {@code Metadata.empty()}.
   */
  public Metadata getMetadata() {
    return Metadata.empty();
  }

  /**
   * Returns the scope instance of the specified type for this context, or {@code null} if no such
   * scope was bound to this context. This method searches parent contexts as well.
   *
   * <p>Implementations which do not support scope types should return {@code null}, which can be
   * achieved by using the default method.
   */
  @NullableDecl
  public LoggingScope getScope(ScopeType type) {
    return null;
  }
}
