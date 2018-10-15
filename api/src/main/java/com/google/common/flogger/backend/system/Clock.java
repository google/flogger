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
 */
public abstract class Clock {
  /**
   * Returns the current time from the epoch (00:00 1st Jan, 1970) with nanosecond granularity,
   * though not necessarily nanosecond precision. This clock measures UTC and is not required to
   * handle leap seconds.
   */
  @SuppressWarnings("GoodTime") // should return a java.time.Instant
  public abstract long getCurrentTimeNanos();
}
