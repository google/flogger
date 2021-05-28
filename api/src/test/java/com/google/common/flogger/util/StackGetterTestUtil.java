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

package com.google.common.flogger.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.util.StackGetterTestUtil.LoggerCode;

final class StackGetterTestUtil {

  private StackGetterTestUtil() {}

  /** Fake class that emulates some code calling a log method. */
  static class UserCode {
    final LoggerCode logger;

    UserCode(LoggerCode library) {
      this.logger = library;
    }

    void invokeUserCode() {
      loggingMethod();
    }

    void loggingMethod() {
      logger.logMethod();
    }
  }

  /** Fake class that emulates the logging library which eventually calls 'findCallerOf()'. */
  static class LoggerCode {
    final int skipCount;
    final StackGetter stackGetter;
    StackTraceElement caller = null;

    public LoggerCode(int skipCount, StackGetter stackGetter) {
      this.skipCount = skipCount;
      this.stackGetter = stackGetter;
    }

    void logMethod() {
      internalMethodOne();
    }

    void internalMethodOne() {
      internalMethodTwo();
    }

    void internalMethodTwo() {
      caller = stackGetter.callerOf(LoggerCode.class, skipCount);
    }
  }

  static void runTestCallerOf(StackGetter stackGetter) {
    // There are 2 internal methods (not including the log method itself) in our fake library.
    LoggerCode library = new LoggerCode(2, stackGetter);
    UserCode code = new UserCode(library);
    code.invokeUserCode();
    assertThat(library.caller.getClassName()).isEqualTo(UserCode.class.getName());
    assertThat(library.caller.getMethodName()).isEqualTo("loggingMethod");
  }

  static void runTestCallerOfBadOffset(StackGetter stackGetter) {
    // If the minimum offset exceeds the number of internal methods, the find fails.
    LoggerCode library = new LoggerCode(3, stackGetter);
    UserCode code = new UserCode(library);
    code.invokeUserCode();
    assertThat(library.caller).isNull();
  }
}
