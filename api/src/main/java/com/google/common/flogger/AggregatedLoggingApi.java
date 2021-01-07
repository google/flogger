/*
 * Copyright (C) 2020 The Flogger Authors.
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
 * The basic aggregated logging API. An implementation of this API (or an extension of it) will be
 * returned by any {@link FluentAggregatedLogger}, and forms the basis of the call chain.
 */
@CheckReturnValue
public interface AggregatedLoggingApi<API extends AggregatedLoggingApi> {
  /**
   * Set aggregated logger time window.
   * If time window is set, aggregated logger will periodically flush log.
   *
   * @param seconds
   * @return
   */
  API withTimeWindow(int seconds);

  int getTimeWindow();

  /**
   * Set aggregated logger number window.
   * <p>
   * If number window is set, aggregated logger will flush log when log number
   * is equal or more than number window.
   *
   * @param number
   * @return
   */
  API withNumberWindow(int number);

  int getNumberWindow();

  /**
   * Check if there are enough data to log based on number window configuration.
   *
   * @return true: flush now; false: not flush.
   */
  boolean shouldFlushByNumber();

  /**
   * Check if there are some data to log.
   *
   * @return the amount of data to be logged
   */
  int haveData();

  /**
   * Format aggregated data to string for logging.
   *
   * @param count the amount of data, 0: all, >0: specified amount
   * @return formatted string content for LogData
   */
  String message(int count);

}
