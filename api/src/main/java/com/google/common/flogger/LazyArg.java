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

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Functional interface for allowing lazily evaluated arguments to be supplied to Flogger. This
 * allows callers to defer argument evaluation efficiently when:
 *
 * <ul>
 *   <li>Doing "fine" logging that's normally disabled
 *   <li>Applying rate limiting to log statements
 * </ul>
 */
public interface LazyArg<T> {
  /**
   * Computes a value to use as a log argument. This method is invoked once the Flogger library has
   * determined that logging will occur, and the returned value is used in place of the {@code
   * LazyArg} instance that was passed into the log statement.
   */
  @NullableDecl
  T evaluate();
}
