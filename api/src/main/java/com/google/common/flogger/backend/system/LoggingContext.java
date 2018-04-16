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

import com.google.common.flogger.backend.Tags;
import java.util.logging.Level;

/**
 * An API for injecting scoped metadata for log statements (either globally or on a per-request
 * basis). This is implemented as an abstract class (rather than an interface) to reduce to risk of
 * breaking existing implementations if the API changes.
 */
public abstract class LoggingContext {
  /**
   * Returns whether the given logger should have logging forced at the specified level. When
   * logging is forced for a log statement it will be emitted regardless or the normal log level
   * configuration of the logger and ignoring any rate limiting or other filtering.
   *
   * <p>A default implementation of this method should simply {@code return false}.
   *
   * <p>WARNING: This method MUST complete quickly and without allocating any memory. It is
   * invoked for every log statement regardless of logging configuration, so any implementation
   * must go to every possible length to be efficient.
   *
   * @param loggerName the fully qualified logger name (e.g. "com.example.SomeClass")
   * @param level the level of the log statement being invoked
   * @param isEnabled whether the logger is enabled at the given level
   */
  public abstract boolean shouldForceLogging(String loggerName, Level level, boolean isEnabled);

  /**
   * Returns a set of tags to be added to a log statement. These tags can be used to provide
   * additional contextual metadata to log statements (e.g. request IDs).
   *
   * <p>A default implementation of this method should simply return {@code Tags.empty()}.
   */
  public abstract Tags getTags();
}
