/*
 * Copyright (C) 2015 The Flogger Authors.
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
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StackBasedLogSiteTest {
  private static final String CLASS_NAME = "com.example.MyClass$Foo";
  private static final String METHOD_NAME = "myMethod";
  private static final int LINE_NUMBER = 1234;
  private static final String FILE_NAME = "MyClass.java";

  @Test
  public void testFields() {
    StackTraceElement element =
        new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER);
    LogSite logSite = new StackBasedLogSite(element);
    assertThat(logSite.getClassName()).isEqualTo(CLASS_NAME);
    assertThat(logSite.getMethodName()).isEqualTo(METHOD_NAME);
    assertThat(logSite.getLineNumber()).isEqualTo(LINE_NUMBER);
    assertThat(logSite.getFileName()).isEqualTo(FILE_NAME);
    assertThat(logSite.getClassName()).isEqualTo(element.getClassName());
    assertThat(logSite.getMethodName()).isEqualTo(element.getMethodName());
    assertThat(logSite.getLineNumber()).isEqualTo(element.getLineNumber());
    assertThat(logSite.getFileName()).isEqualTo(element.getFileName());
  }

  @Test
  public void testNulls() {
    try {
      new StackBasedLogSite(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void testUnknownLineNumber() {
    // Negative line numbers are technically possible.
    assertThat(stackBasedLogSite(CLASS_NAME, METHOD_NAME, null, -2).getLineNumber())
        .isEqualTo(LogSite.UNKNOWN_LINE);
  }

  @Test
  public void testEqualsAndHashCode() {
    LogSite logSite = stackBasedLogSite(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER);
    assertThat(logSite.equals(null)).isFalse();

    LogSite otherLogSiteOnSameLine =
        new StackBasedLogSite(
            new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
    // This is really unfortunate, but there's no way to distinguish two log sites on the same line.
    assertThat(otherLogSiteOnSameLine).isEqualTo(logSite);

    List<LogSite> sites =
        Arrays.asList(
            logSite,
            stackBasedLogSite("com/example/MyOtherClass", METHOD_NAME, FILE_NAME, LINE_NUMBER),
            stackBasedLogSite(CLASS_NAME, "otherMethod", FILE_NAME, LINE_NUMBER),
            stackBasedLogSite(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER + 1));
    // Asserts total uniqueness between all elements.
    assertThat(sites).containsNoDuplicates();
    Set<Integer> hashCodes = new HashSet<Integer>();
    for (LogSite site : sites) {
      hashCodes.add(site.hashCode());
    }
    // Asserts all the hash codes were unique as well.
    assertThat(hashCodes).hasSize(sites.size());
  }

  // Helper to keep callers a bit less cluttered and make tests a bit more readable.
  private static StackBasedLogSite stackBasedLogSite(
      String className, String methodName, String fileName, int lineNumber) {
    return new StackBasedLogSite(
        new StackTraceElement(className, methodName, fileName, lineNumber));
  }
}
