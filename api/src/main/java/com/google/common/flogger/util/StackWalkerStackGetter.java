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

import java.lang.StackWalker.StackFrame;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * StackWalker based implementation of the {@link StackGetter} interface.
 *
 * <p>Note, that since this is using Java 9 api, it is being compiled separately from the rest of
 * the source code.
 */
final class StackWalkerStackGetter implements StackGetter {
  private static final StackWalker STACK_WALKER =
      StackWalker.getInstance(StackWalker.Option.SHOW_REFLECT_FRAMES);

  public StackWalkerStackGetter() {
    // Due to b/241269335, we check in constructor whether this implementation crashes in runtime,
    // and CallerFinder should catch any Throwable caused.
    StackTraceElement unused = callerOf(StackWalkerStackGetter.class, 0);
  }

  @Override
  public StackTraceElement callerOf(Class<?> target, int skipFrames) {
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    return STACK_WALKER.walk(
        s ->
            filterStackTraceAfterTarget(isTargetClass(target), skipFrames, s)
                .findFirst()
                .orElse(null));
  }

  @Override
  public StackTraceElement[] getStackForCaller(Class<?> target, int maxDepth, int skipFrames) {
    checkArgument(maxDepth == -1 || maxDepth > 0, "maxDepth must be > 0 or -1");
    checkArgument(skipFrames >= 0, "skipFrames must be >= 0");
    return STACK_WALKER.walk(
        s ->
            filterStackTraceAfterTarget(isTargetClass(target), skipFrames, s)
                .limit(maxDepth == -1 ? Long.MAX_VALUE : maxDepth)
                .toArray(StackTraceElement[]::new));
  }

  private Predicate<StackFrame> isTargetClass(Class<?> target) {
    String name = target.getName();
    return f -> f.getClassName().equals(name);
  }

  private Stream<StackTraceElement> filterStackTraceAfterTarget(
      Predicate<StackFrame> isTargetClass, int skipFrames, Stream<StackFrame> s) {
    // need to skip + 1 because of the call to the method this method is being called from
    return s.skip(skipFrames + 1)
        // skip all classes which don't match the name we are looking for
        .dropWhile(isTargetClass.negate())
        // then skip all which matches
        .dropWhile(isTargetClass)
        .map(StackFrame::toStackTraceElement);
  }
}
