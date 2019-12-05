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

/**
 * A clock to return walltime timestamps for log statements. This is implemented as an abstract
 * class (rather than an interface) to reduce to risk of breaking existing implementations if the
 * API changes.
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
public abstract class Clock {
  /**
   * Returns the current time from the epoch (00:00 1st Jan, 1970) with nanosecond granularity,
   * though not necessarily nanosecond precision. This clock measures UTC and is not required to
   * handle leap seconds.
   */
  public abstract long getCurrentTimeNanos();
}
