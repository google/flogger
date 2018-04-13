/*
 * Copyright (C) 2012 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.errorprone.annotations.CheckReturnValue;
import javax.annotation.Nullable;

/** A helper class for determining callers of a specified class currently on the stack. */
@CheckReturnValue
public final class CallerFinder {
  private static final FastStackGetter stackGetter = FastStackGetter.createIfSupported();

  /**
   * Returns the stack trace element of the immediate caller of the specified class.
   *
   * @param target the target class whose callers we are looking for.
   * @param throwable a new Throwable made at a known point in the call hierarchy.
   * @param skip the minimum number of calls known to have occurred between the first call to
   *     the target class and the point at which the specified throwable was created. If in doubt,
   *     specify zero here to avoid accidentally skipping past the caller.
   * @return the stack trace element representing the immediate caller of the specified class, or
   *     null if no caller was found (due to incorrect target, wrong skip count or use of JNI).
   */
  @Nullable
  public static StackTraceElement findCallerOf(Class<?> target, Throwable throwable, int skip) {
    checkNotNull(target, "target");
    checkNotNull(throwable, "throwable");
    if (skip < 0) {
      throw new IllegalArgumentException("skip count cannot be negative: " + skip);
    }
    // Getting the full stack trace is expensive, so avoid it where possible.
    StackTraceElement[] stack = (stackGetter != null) ? null : throwable.getStackTrace();

    // Note: To avoid having to reflect the getStackTraceDepth() method as well, we assume that we
    // will find the caller on the stack and simply catch an exception if we fail (which should
    // hardly ever happen).
    boolean foundCaller = false;
    try {
      for (int index = skip; ; index++) {
        StackTraceElement element =
            (stackGetter != null)
                ? stackGetter.getStackTraceElement(throwable, index)
                : stack[index];
        if (target.getName().equals(element.getClassName())) {
          foundCaller = true;
        } else if (foundCaller) {
          return element;
        }
      }
    } catch (Exception e) {
      // This should only happen is the caller was not found on the stack (getting exceptions from
      // the stack trace method should never happen) and it should only be an
      // IndexOutOfBoundsException, however we don't want _anything_ to be thrown from here.
      // TODO(user): Change to only catch IndexOutOfBoundsException and test _everything_.
      return null;
    }
  }

  /**
   * Returns a synthetic stack trace starting at the immediate caller of the specified target.
   *
   * @param target the class who caller the returned stack trace will start at.
   * @param throwable a new Throwable made at a known point in the call hierarchy.
   * @param skip the minimum number of calls known to have occurred between the first call to
   *     the target class and the point at which the specified throwable was created. If in doubt,
   *     specify zero here to avoid accidentally skipping past the caller.
   * @param maxDepth the maximum size of the returned stack (pass -1 for the complete stack).
   * @return a synthetic stack trace starting at the immediate caller of the specified target, or
   *     the empty array if no caller was found (due to incorrect target, wrong skip count or use
   *     of JNI).
   */
  @Nullable
  public static StackTraceElement[] getStackForCallerOf(
      Class<?> target, Throwable throwable, int skip, int maxDepth) {
    checkNotNull(target, "target");
    checkNotNull(throwable, "throwable");
    if (skip < 0) {
      throw new IllegalArgumentException("skip count cannot be negative: " + skip);
    }
    if (maxDepth <= 0 && maxDepth != -1) {
      throw new IllegalArgumentException("invalid maximum depth: " + maxDepth);
    }
    // Getting the full stack trace is expensive, so avoid it where possible.
    StackTraceElement[] stack;
    int depth;
    if (stackGetter != null) {
      stack = null;
      depth = stackGetter.getStackTraceDepth(throwable);
    } else {
      stack = throwable.getStackTrace();
      depth = stack.length;
    }
    boolean foundCaller = false;
    for (int index = skip; index < depth; index++) {
      StackTraceElement element =
          (stackGetter != null) ? stackGetter.getStackTraceElement(throwable, index) : stack[index];
      if (target.getName().equals(element.getClassName())) {
        foundCaller = true;
      } else if (foundCaller) {
        // There must be at least one element to add (maxDepth > 0 and n < depth).
        int elementsToAdd = depth - index;
        if (maxDepth > 0 && maxDepth < elementsToAdd) {
          elementsToAdd = maxDepth;
        }
        StackTraceElement[] syntheticStack = new StackTraceElement[elementsToAdd];
        syntheticStack[0] = element;
        for (int n = 1; n < elementsToAdd; n++) {
          syntheticStack[n] =
              (stackGetter != null)
                  ? stackGetter.getStackTraceElement(throwable, index + n)
                  : stack[index + n];
        }
        return syntheticStack;
      }
    }
    return new StackTraceElement[0];
  }
}
