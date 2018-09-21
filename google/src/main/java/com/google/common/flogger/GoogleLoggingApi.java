/*
 * Copyright (C) 2013 The Flogger Authors.
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

import com.google.common.flogger.util.Checks;
import com.google.errorprone.annotations.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Google specific extensions to the fluent logging API.
 * <p>
 * If a team wishes to implement its own logging extensions, it must extend this API (in the same
 * way that this API was extended from LoggingApi) and extend {@link GoogleLogContext} similarly.
 * This should all be tied together by implementing an alternate implementation of GoogleLogger.
 * <p>
 * However, if you wish to extend the default logging API, please contact flogger-dev@ first.
 *
 * @param <API> The api returned during method chaining (possibly an extension of this interface).
 */
// NOTE: new methods to this interface should be coordinated with google-java-format
// TODO(dbeaumont): Now that this class offers no additional methods, consider removing it.
@CheckReturnValue
public interface GoogleLoggingApi<API extends GoogleLoggingApi<API>> extends LoggingApi<API> {
  /**
   * An implementation of {@link GoogleLoggingApi} which does nothing and discards all parameters.
   */
  public static class NoOp<API extends GoogleLoggingApi<API>> extends LoggingApi.NoOp<API>
      implements GoogleLoggingApi<API> {

    @Override
    public final <T> API with(MetadataKey<T> key, @Nullable T value) {
      // Identical to the check in GoogleLogContext for consistency.
      Checks.checkNotNull(key, "metadata key");
      return noOp();
    }
  }

  /**
   * Associates a metadata key constant with a runtime value for this log statement in a structured
   * way that is accessible to logger backends.
   *
   * <p>This method is not a replacement for general parameter passing in the {@link #log()} method
   * and should be reserved for keys/values with specific semantics. Examples include:
   * <ul>
   *   <li>Keys that are recognised by specific logger backends (typically to control logging
   *       behaviour in some way).
   *   <li>Key value pairs which are explicitly extracted from logs by tools.
   * </ul>
   *
   * <p>Metadata keys can support repeated values (see {@link MetadataKey#canRepeat()}), and if a
   * repeatable key is used multiple times in the same log statement, the effect is to collect all
   * the given values in order. If a non-repeatable key is passed multiple times, only the last
   * value is retained (though callers should not rely on this behavior and should simply avoid
   * repeating non-repeatable keys).
   *
   * <p>If {@code value} is {@code null}, this method is a no-op. This is useful for specifying
   * conditional values (e.g. via {@code logger.atInfo().with(MY_KEY, getValueOrNull()).log(...)}).
   *
   * @param key the metadata key (expected to be a static constant)
   * @param value a value to be associated with the key in this log statement. Null values are
   *        allowed, but the effect is always a no-op
   * @throws NullPointerException if the given key is null
   * @see MetadataKey
   */
  <T> API with(MetadataKey<T> key, @Nullable T value);
}
