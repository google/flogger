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
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FastStackGetterTest {
  /**
   * A sanity check that the platform we are using really does support a fast stack access method.
   * If this fails for a given platform of configuration, functionality is unaffected but there
   * will be a significant performance penalty for looking up caller information in the stack.
   * <p>
   * The performance issues can be avoided by configuring Flogger to use a sub-class of LoggerAgent
   * that avoids stack access altogether, or switching to a different JDK.
   * <p>
   * This test serves only as a warning for degrading performance and can be disabled without any
   * risk to functionality.
   */
  @Test
  public void testPlatformSupportsFaskStackAccess() {
    assertThat(FastStackGetter.createIfSupported()).isNotNull();
  }

  /** Fake class that emulates some code calling different methods. */
  private class UserCode {
    final Throwable throwable;

    UserCode() {
      throwable = methodA();
    }

    Throwable methodA() {
      return methodB();
    }

    Throwable methodB() {
      return methodC();
    }

    Throwable methodC() {
      return new Throwable();
    }
  }

  @Test
  public void testFastStackGetter() {
    FastStackGetter stackGetter = FastStackGetter.createIfSupported();
    Throwable throwable = new UserCode().throwable;

    // Test expected stack entries.
    assertThat(stackGetter.getStackTraceElement(throwable, 0).getMethodName()).isEqualTo("methodC");
    assertThat(stackGetter.getStackTraceElement(throwable, 1).getMethodName()).isEqualTo("methodB");
    assertThat(stackGetter.getStackTraceElement(throwable, 2).getMethodName()).isEqualTo("methodA");

    StackTraceElement[] stack = throwable.getStackTrace();
    // We added at least 5 method calls to the stack before creating throwable.
    assertThat(stack.length).isAtLeast(5);
    assertThat(stackGetter.getStackTraceDepth(throwable)).isEqualTo(stack.length);
    for (int n = 0; n < stack.length; n++) {
      assertThat(stackGetter.getStackTraceElement(throwable, n)).isEqualTo(stack[n]);
    }

    // Finally ensure we blow up if the stack index is out of bounds.
    try {
      StackTraceElement unused = stackGetter.getStackTraceElement(throwable, stack.length);
      fail("expected IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      StackTraceElement unused = stackGetter.getStackTraceElement(throwable, -1);
      fail("expected IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException expected) {
    }
  }
}
