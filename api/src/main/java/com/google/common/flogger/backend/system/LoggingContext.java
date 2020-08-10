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

import com.google.common.flogger.context.Tags;
import java.util.logging.Level;

/**
 * An API for injecting scoped metadata for log statements (either globally or on a per-request
 * basis). This is implemented as an abstract class (rather than an interface) to reduce to risk of
 * breaking existing implementations if the API changes.
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
public abstract class LoggingContext {
  /**
   * Returns whether the given logger should have logging forced at the specified level. When
   * logging is forced for a log statement it will be emitted regardless or the normal log level
   * configuration of the logger and ignoring any rate limiting or other filtering.
   *
   * <p>A default implementation of this method should simply {@code return false}.
   *
   * <p>{@code loggerName} can be used to look up specific configuration, such as log level, for
   * the logger, to decide if a log statement should be forced. This information might vary
   * depending on the context in which this call is made, so the result should not be cached.
   *
   * <p>{@code isEnabledByLevel} indicates that the log statement is enabled according to its log
   * level, but a {@code true} value does not necessarily indicate that logging will occur, due to
   * rate limiting or other conditional logging mechanisms. To bypass conditional logging and
   * ensure that an enabled log statement will be emitted, this method should return {@code true}
   * if {@code isEnabledByLevel} was {@code true}.
   *
   * <p>WARNING: This method MUST complete quickly and without allocating any memory. It is
   * invoked for every log statement regardless of logging configuration, so any implementation
   * must go to every possible length to be efficient.
   *
   * @param loggerName the fully qualified logger name (e.g. "com.example.SomeClass")
   * @param level the level of the log statement being invoked
   * @param isEnabledByLevel whether the logger is enabled at the given level
   */
  public abstract boolean shouldForceLogging(
      String loggerName, Level level, boolean isEnabledByLevel);

  /**
   * Returns a set of tags to be added to a log statement. These tags can be used to provide
   * additional contextual metadata to log statements (e.g. request IDs).
   *
   * <p>A default implementation of this method should simply return {@code Tags.empty()}.
   */
  public abstract Tags getTags();
}
