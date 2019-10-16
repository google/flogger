/*
 * Copyright (C) 2016 The Flogger Authors.
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
 * A synthetic exception which can be attached to log statements when additional stack trace
 * information is required in log files or via tools such as ECatcher.
 * <p>
 * The name of this class may become relied upon implicitly by tools such as ECatcher. Do not
 * rename or move this class without checking for implicit in logging tools.
 */
public final class LogSiteStackTrace extends Exception {
  /**
   * Creates a synthetic exception to hold a call-stack generated for the log statement itself.
   * <p>
   * This exception is never expected to actually get thrown or caught at any point.
   *
   * @param cause the optional cause (set via withCause() in the log statement).
   * @param stackSize the requested size of the synthetic stack trace (actual trace can be shorter).
   * @param syntheticStackTrace the synthetic stack trace starting at the log statement.
   */
  LogSiteStackTrace(
      @NullableDecl Throwable cause, StackSize stackSize, StackTraceElement[] syntheticStackTrace) {
    super(stackSize.toString(), cause);
    // This takes a defensive copy, but there's no way around that. Note that we cannot override
    // getStackTrace() to avoid a defensive copy because that breaks stack trace formatting
    // (which doesn't call getStackTrace() directly). See b/27310448.
    setStackTrace(syntheticStackTrace);
  }

  // We override this because it gets called from the superclass constructor and we don't want
  // it to do any work (we always replace it immediately).
  @SuppressWarnings("UnsynchronizedOverridesSynchronized")
  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
