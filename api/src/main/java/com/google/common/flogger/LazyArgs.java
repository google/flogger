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

package com.google.common.flogger;

import static com.google.common.flogger.util.Checks.checkNotNull;

/**
 * Static utility methods for lazy argument evaluation in Flogger. The {@link #lazy(LazyArg)}
 * method allows lambda expressions to be "cast" to the {@link LazyArg} interface.
 *
 * <p>In cases where the log statement is strongly expected to always be enabled (e.g. unconditional
 * logging at warning or above) it may not be worth using lazy evaluation because any work required
 * to evaluate arguments will happen anyway.
 *
 * <p>If lambdas are available, users should prefer using this class rather than explicitly creating
 * {@code LazyArg} instances.
 */
// TODO: Add other generally useful methods here, especially things which help non-lambda users.
public final class LazyArgs {
  /**
   * Coerces a lambda expression or method reference to return a lazily evaluated logging argument.
   * Pass in a compatible, no-argument, lambda expression or method reference to have it evaluated
   * only when logging will actually occur.
   *
   * <pre>{@code
   * logger.atFine().log("value=%s", lazy(() -> doExpensive()));
   * logger.atWarning().atMostEvery(5, MINUTES).log("value=%s", lazy(stats::create));
   * }</pre>
   *
   * Evaluation of lazy arguments occurs at most once, and always in the same thread from which the
   * logging call was made.
   *
   * <p>Note also that it is almost never suitable to make a {@code toString()} call "lazy" using
   * this mechanism and, in general, explicitly calling {@code toString()} on arguments which are
   * being logged is an error as it precludes the ability to log an argument structurally.
   */
  public static <T> LazyArg<T> lazy(LazyArg<T> lambdaOrMethodReference) {
    // This method is essentially a coercing cast for the functional interface to give the compiler
    // a target type to convert a lambda expression or method reference into.
    return checkNotNull(lambdaOrMethodReference, "lazy arg");
  }

  private LazyArgs() {}
}
