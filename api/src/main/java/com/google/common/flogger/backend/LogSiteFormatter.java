/*
 * Copyright (C) 2023 The Flogger Authors.
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

package com.google.common.flogger.backend;

import com.google.common.flogger.LogSite;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Interface for custom LogSite formatting. */
public interface LogSiteFormatter {

  /**
   * Appends log site information to a buffer.
   *
   * @param logSite the log site to be appended (ignored if {@link LogSite#INVALID}).
   * @param out the destination buffer.
   * @return whether the logSite was appended.
   */
  @CanIgnoreReturnValue
  boolean appendLogSite(LogSite logSite, StringBuilder out);
}
