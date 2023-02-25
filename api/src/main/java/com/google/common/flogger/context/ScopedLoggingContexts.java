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

import com.google.common.flogger.MetadataKey;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

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
  public static ScopedLoggingContext.Builder newContext() {
    return ScopedLoggingContext.getInstance().newContext();
  }

  /**
   * Adds tags to the current set of log tags for the current context. Tags are merged together and
   * existing tags cannot be modified. This is deliberate since two pieces of code may not know
   * about each other and could accidentally use the same tag name; in that situation it's important
   * that both tag values are preserved.
   *
   * <p>Furthermore, the types of data allowed for tag values are strictly controlled. This is also
   * very deliberate since these tags must be efficiently added to every log statement and so it's
   * important that they resulting string representation is reliably cacheable and can be calculated
   * without invoking arbitrary code (e.g. the {@code toString()} method of some unknown user type).
   *
   * @return false if there is no current context, or scoped contexts are not supported.
   */
  @CanIgnoreReturnValue
  public static boolean addTags(Tags tags) {
    return ScopedLoggingContext.getInstance().addTags(tags);
  }

  /**
   * Adds a single metadata key/value pair to the current context.
   *
   * <p>Unlike {@link Tags}, which have a well defined value ordering, independent of the order in
   * which values were added, context metadata preserves the order of addition. As such, it is not
   * advised to add values for the same metadata key from multiple threads, since that may create
   * non-deterministic ordering. It is recommended (where possible) to add metadata when building a
   * new context, rather than adding it to context visible to multiple threads.
   */
  @CanIgnoreReturnValue
  public static <T> boolean addMetadata(MetadataKey<T> key, T value) {
    return ScopedLoggingContext.getInstance().addMetadata(key, value);
  }

  /**
   * Applies the given log level map to the current context. Log level settings are merged with any
   * existing setting from the current (or parent) contexts such that logging will be enabled for a
   * log statement if:
   *
   * <ul>
   *   <li>It was enabled by the given map.
   *   <li>It was already enabled by the current context.
   * </ul>
   *
   * <p>The effects of this call will be undone only when the current context terminates.
   *
   * @return false if there is no current context, or scoped contexts are not supported.
   */
  @CanIgnoreReturnValue
  public static <T> boolean applyLogLevelMap(LogLevelMap logLevelMap) {
    return ScopedLoggingContext.getInstance().applyLogLevelMap(logLevelMap);
  }
}
