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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.LogSite;
import com.google.common.flogger.testing.FakeLogSite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogSiteFormattersTest {

  @Test
  public void testInvalidLogSite() {
    for (LogSiteFormatter formatter : LogSiteFormatters.values()) {
      StringBuilder out = new StringBuilder();
      assertThat(formatter.appendLogSite(LogSite.INVALID, out)).isFalse();
      assertThat(out.toString()).isEmpty();
    }
  }

  @Test
  public void testAppendLogSite_default() {
    StringBuilder out = new StringBuilder();
    LogSite logSite = FakeLogSite.create("<class>", "<method>", 32, "Ignored.java");
    assertThat(LogSiteFormatters.DEFAULT.appendLogSite(logSite, out)).isTrue();
    assertThat(out.toString()).isEqualTo("<class>.<method>:32");
  }

  @Test
  public void testAppendLogSite_noOp() {
    StringBuilder out = new StringBuilder();
    LogSite logSite = FakeLogSite.create("<class>", "<method>", 32, "Ignored.java");
    assertThat(LogSiteFormatters.NO_OP.appendLogSite(logSite, out)).isFalse();
    assertThat(out.toString()).isEmpty();
  }

  @Test
  public void testAppendLogSite_simpleClassname_qualifiedName_unqualified() {
    StringBuilder out = new StringBuilder();
    LogSite logSite =
        FakeLogSite.create(
            "com.google.common.flogger.backend.LogSiteFormattersTest",
            "testMethod",
            42,
            "Ignored.java");
    assertThat(LogSiteFormatters.SIMPLE_CLASSNAME.appendLogSite(logSite, out)).isTrue();
    assertThat(out.toString()).isEqualTo("LogSiteFormattersTest.testMethod:42");
  }

  @Test
  public void testAppendLogSite_simpleClassname_unqualifiedName_all() {
    StringBuilder out = new StringBuilder();
    LogSite logSite = FakeLogSite.create("LogSiteFormattersTest", "testMethod", 55, "Ignored.java");
    assertThat(LogSiteFormatters.SIMPLE_CLASSNAME.appendLogSite(logSite, out)).isTrue();
    assertThat(out.toString()).isEqualTo("LogSiteFormattersTest.testMethod:55");
  }

  @Test
  public void testAppendLogSite_simpleClassname_trailingDot_all() {
    StringBuilder out = new StringBuilder();
    LogSite logSite =
        FakeLogSite.create("LogSiteFormattersTest.", "testMethod", 63, "Ignored.java");
    assertThat(LogSiteFormatters.SIMPLE_CLASSNAME.appendLogSite(logSite, out)).isTrue();
    assertThat(out.toString()).isEqualTo("LogSiteFormattersTest..testMethod:63");
  }

  @Test
  public void testAppendLogSite_simpleClassname_fakeClassName() {
    StringBuilder out = new StringBuilder();
    LogSite logSite = FakeLogSite.create("<class>", "<method>", 32, "Ignored.java");
    assertThat(LogSiteFormatters.SIMPLE_CLASSNAME.appendLogSite(logSite, out)).isTrue();
    assertThat(out.toString()).isEqualTo("<class>.<method>:32");
  }
}
