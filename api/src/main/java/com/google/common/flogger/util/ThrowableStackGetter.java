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

import static com.google.common.flogger.util.Checks.checkArgument;

/** Default implementation of {@link StackGetter} using {@link Throwable#getStackTrace}. */
final class ThrowableStackGetter implements StackGetter {

  @Override
  public StackTraceElement callerOf(Class<?> target, int skipFrames) {
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    StackTraceElement[] stack = new Throwable().getStackTrace();
    int callerIndex = findCallerIndex(stack, target, skipFrames + 1);
    if (callerIndex != -1) {
      return stack[callerIndex];
    }

    return null;
  }

  @Override
  public StackTraceElement[] getStackForCaller(Class<?> target, int maxDepth, int skipFrames) {
    checkArgument(maxDepth == -1 || maxDepth > 0, "maxDepth must be > 0 or -1");
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    StackTraceElement[] stack = new Throwable().getStackTrace();
    int callerIndex = findCallerIndex(stack, target, skipFrames + 1);
    if (callerIndex == -1) {
      return new StackTraceElement[0];
    }
    int elementsToAdd = stack.length - callerIndex;
    if (maxDepth > 0 && maxDepth < elementsToAdd) {
      elementsToAdd = maxDepth;
    }
    StackTraceElement[] stackTrace = new StackTraceElement[elementsToAdd];
    System.arraycopy(stack, callerIndex, stackTrace, 0, elementsToAdd);
    return stackTrace;
  }

  private int findCallerIndex(StackTraceElement[] stack, Class<?> target, int skipFrames) {
    boolean foundCaller = false;
    String targetClassName = target.getName();
    for (int frameIndex = skipFrames; frameIndex < stack.length; frameIndex++) {
      if (stack[frameIndex].getClassName().equals(targetClassName)) {
        foundCaller = true;
      } else if (foundCaller) {
        return frameIndex;
      }
    }
    return -1;
  }
}
