/*
 * Copyright (C) 2021 The Flogger Authors.
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

package com.google.common.flogger.util;

import java.io.Closeable;

/**
 * A threal local counter, incremented whenever a log statement is being processed by the
 * backend. If this value is greater than 1, then reentrant logging has occured, and some code may
 * behave differently to try and avoid issues such as unbounded recursion. Logging may even be
 * disabled completely if the depth gets too high.
 *
 * <p>This class is an internal detail and must not be used outside the core Flogger library.
 * Backends which need to know the recursion depth for any reason should call {@code
 * Platform.getCurrentRecursionDepth()}.
 */
public final class RecursionDepth implements Closeable {
  private static final ThreadLocal<RecursionDepth> holder = new ThreadLocal<RecursionDepth>() {
    @Override
    protected RecursionDepth initialValue() {
      return new RecursionDepth();
    }
  };

  /** Do not call this method directly, use {@code Platform.getCurrentRecursionDepth()}. */
  public static int getCurrentDepth() {
    return holder.get().value;
  }

  /** Do not call this method directly, use {@code Platform.getCurrentRecursionDepth()}. */
  public int getValue() {
    return value;
  }

  /** Internal API for use by core Flogger library. */
  public static RecursionDepth enterLogStatement() {
    RecursionDepth depth = holder.get();
    // Can only reach 0 if it wrapped around completely or someone is manipulating the value badly.
    // We really don't expect 2^32 levels of recursion however, so assume it's a bug.
    if (++depth.value == 0) {
      throw new AssertionError("Overflow of RecursionDepth (possible error in core library)");
    }
    return depth;
  }

  private int value = 0;

  @Override
  public void close() {
    if (value > 0) {
      value -= 1;
      return;
    }
    // This should never happen if the only callers are inside core library.
    throw new AssertionError("Mismatched calls to RecursionDepth (possible error in core library)");
  }
}
