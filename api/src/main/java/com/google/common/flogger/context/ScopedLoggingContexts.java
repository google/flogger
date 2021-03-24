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

package com.google.common.flogger.context;

import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Static methods equivalent to the instance methods on {@link ScopedLoggingContext} but which
 * always operate on the current {@link ScopedLoggingContext} that would be returned by {@link
 * ScopedLoggingContext#getInstance}.
 */
public final class ScopedLoggingContexts {

  private ScopedLoggingContexts() {}

  /**
   * Creates a new {@link ScopedLoggingContext.Builder} to which additional logging metadata can be
   * attached before being installed or used to wrap some existing code.
   *
   * <pre>{@code
   * Foo result = ScopedLoggingContexts.newContext()
   *     .withTags(Tags.of("my_tag", someValue))
   *     .call(MyClass::doFoo);
   * }</pre>
   */
  @CheckReturnValue
  public static ScopedLoggingContext.Builder newContext() {
    return ScopedLoggingContext.getInstance().newContext();
  }
}
