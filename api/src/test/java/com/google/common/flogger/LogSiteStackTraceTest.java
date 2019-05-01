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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogSiteStackTraceTest {
  private static final ImmutableList<StackTraceElement> FAKE_STACK =
      ImmutableList.of(
          new StackTraceElement("FirstClass", "method1", "Source1.java", 111),
          new StackTraceElement("SecondClass", "method2", "Source2.java", 222),
          new StackTraceElement("ThirdClass", "method3", "Source3.java", 333));

  @Test
  public void testGetMessage() {
    LogSiteStackTrace trace = new LogSiteStackTrace(null, StackSize.FULL, new StackTraceElement[0]);
    assertThat(trace).hasMessageThat().isEqualTo("FULL");
    assertThat(trace.getCause()).isNull();
  }

  @Test
  public void testGetCause() {
    Throwable cause = new RuntimeException();
    LogSiteStackTrace trace =
        new LogSiteStackTrace(cause, StackSize.SMALL, new StackTraceElement[0]);
    assertThat(trace.getCause()).isSameInstanceAs(cause);
  }

  @Test
  public void testGetStackTrace() {
    StackTraceElement[] stack = FAKE_STACK.toArray(new StackTraceElement[0]);
    LogSiteStackTrace trace = new LogSiteStackTrace(null, StackSize.SMALL, stack);
    assertThat(trace.getStackTrace()).isNotSameInstanceAs(stack);
    assertThat(trace.getStackTrace()).asList().isEqualTo(FAKE_STACK);
  }
}
