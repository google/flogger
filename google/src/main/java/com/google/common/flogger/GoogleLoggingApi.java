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

import com.google.errorprone.annotations.CheckReturnValue;

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
      implements GoogleLoggingApi<API> { }
}
