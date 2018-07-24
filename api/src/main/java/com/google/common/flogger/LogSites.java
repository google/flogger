/*
 * Copyright (C) 2018 The Flogger Authors.
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

import com.google.common.flogger.backend.Platform;

/**
 * Helper class to generate log sites for the current line of code. This class is deliberately
 * isolated (rather than having the method in {@link LogSite} itself) because manual log site
 * injection is rare and by isolating it into a separate class may help encourage users to think
 * carefully about the issue.
 *
 */
public final class LogSites {
  /**
   * Returns a {@code LogSite} for the current line of code. This can be used in conjunction with
   * the {@link LoggingApi#withInjectedLogSite(LogSite)} method to implement logging helper
   * methods. In some platforms, log site determination may be unsupported, and in those cases this
   * method will always return the {@link LogSite#INVALID} instance.
   * <p>
   * It is very important to note that this method can be very slow, since determining the log site
   * can involve stack trace analysis. It is only recommended that it is used for cases where
   * logging is expected to occur (e.g. {@code WARNING} level or above). Implementing a helper
   * method for {@code FINE} logging is usually unnecessary (it doesn't normally need to follow any
   * specific "best practice" behavior).
   * <p>
   * Note that even when log site determination is supported, it is not defined as to whether two
   * invocations of this method on the same line of code will produce the same instance, equivalent
   * instances or distinct instance. Thus you should never invoke this method twice in a single
   * statement (and you should never need to).
   * <p>
   * Note that this method call may be replaced in compiled applications via bytecode manipulation
   * or other mechanisms to improve performance.
   */
  public static LogSite logSite() {
    return Platform.getCallerFinder().findLogSite(LogSites.class, 0);
  }

  private LogSites() {}
}
