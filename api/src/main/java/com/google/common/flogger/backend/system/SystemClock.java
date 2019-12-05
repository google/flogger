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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Default millisecond precision clock.
 *
 * <p>See class documentation in {@link Clock} for important implementation restrictions.
 */
public final class SystemClock extends Clock {
  private static final SystemClock INSTANCE = new SystemClock();

  // Called during logging platform initialization; MUST NOT call any code that might log.
  public static SystemClock getInstance() {
    return INSTANCE;
  }

  private SystemClock() { }

  @Override
  public long getCurrentTimeNanos() {
    return MILLISECONDS.toNanos(System.currentTimeMillis());
  }

  @Override
  public String toString() {
    return "Default millisecond precision clock";
  }
}
