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

import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

/**
 * {@link JavaLangAccess} based implementation of {@link StackGetter}.
 *
 * <p>Note. This is being compiled separate from the rest of the code, because it uses Java 8
 * private api.
 */
final class JavaLangAccessStackGetter implements StackGetter {
  private static final JavaLangAccess access = SharedSecrets.getJavaLangAccess();

  @Override
  public StackTraceElement callerOf(Class<?> target, int skipFrames) {
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    Throwable throwable = new Throwable();
    int index = findCallerIndex(throwable, target, skipFrames + 1);
    return index == -1 ? null : access.getStackTraceElement(throwable, index);
  }

  @Override
  public StackTraceElement[] getStackForCaller(Class<?> target, int maxDepth, int skipFrames) {
    checkArgument(maxDepth == -1 || maxDepth > 0, "maxDepth must be > 0 or -1");
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    Throwable throwable = new Throwable();
    int callerIndex = findCallerIndex(throwable, target, skipFrames + 1);
    if (callerIndex == -1) {
      return new StackTraceElement[0];
    }
    int elementsToAdd = access.getStackTraceDepth(throwable) - callerIndex;
    if (maxDepth > 0 && maxDepth < elementsToAdd) {
      elementsToAdd = maxDepth;
    }
    StackTraceElement[] stackTrace = new StackTraceElement[elementsToAdd];
    for (int i = 0; i < elementsToAdd; i++) {
      stackTrace[i] = access.getStackTraceElement(throwable, callerIndex + i);
    }
    return stackTrace;
  }

  private int findCallerIndex(Throwable throwable, Class<?> target, int skipFrames) {
    int depth = access.getStackTraceDepth(throwable);
    boolean foundCaller = false;
    String targetClassName = target.getName();
    for (int index = skipFrames; index < depth; index++) {
      if (access.getStackTraceElement(throwable, index).getClassName().equals(targetClassName)) {
        foundCaller = true;
      } else if (foundCaller) {
        return index;
      }
    }
    return -1;
  }
}
