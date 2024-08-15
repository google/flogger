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

package com.google.common.flogger.util;

import static com.google.common.truth.Truth.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CallerFinderTest {
  /**
   * A sanity check in case we ever discover a platform where the class name in the stack trace does
   * not match Class.getName() - this is never quite guaranteed by the JavaDoc in the JDK but is
   * relied upon during log site analysis.
   */
  @Test
  public void testStackTraceUsesClassGetName() {
    // Simple case for a top-level named class.
    assertThat(new Throwable().getStackTrace()[0].getClassName())
        .isEqualTo(CallerFinderTest.class.getName());

    // Anonymous inner class.
    Object obj =
        new Object() {
          @Override
          public String toString() {
            return new Throwable().getStackTrace()[0].getClassName();
          }
        };
    assertThat(obj.toString()).isEqualTo(obj.getClass().getName());
  }

  /** Fake class that emulates some code calling a log method. */
  private static class UserCode {
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
  private static class LoggerCode {
    final int skipCount;
    @Nullable StackTraceElement caller = null;

    public LoggerCode(int skipCount) {
      this.skipCount = skipCount;
    }

    void logMethod() {
      internalMethodOne();
    }

    void internalMethodOne() {
      internalMethodTwo();
    }

    void internalMethodTwo() {
      caller = CallerFinder.findCallerOf(LoggerCode.class, skipCount);
    }
  }

  @Test
  public void testFindCallerOf() {
    // There are 2 internal methods (not including the log method itself) in our fake library.
    LoggerCode library = new LoggerCode(2);
    UserCode code = new UserCode(library);
    code.invokeUserCode();
    assertThat(library.caller.getClassName()).isEqualTo(UserCode.class.getName());
    assertThat(library.caller.getMethodName()).isEqualTo("loggingMethod");
  }

  @Test
  public void testFindCallerOfBadOffset() {
    // If the minimum offset exceeds the number of internal methods, the find fails.
    LoggerCode library = new LoggerCode(3);
    UserCode code = new UserCode(library);
    code.invokeUserCode();
    assertThat(library.caller).isNull();
  }
}
