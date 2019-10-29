/*
 * Copyright (C) 2017 The Flogger Authors.
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

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LoggerConfigTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static class MemberClass {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  }

  @Test
  public void testFromLogger() {
    LoggerConfig config = LoggerConfig.of(logger);
    assertThat(config.getName()).isEqualTo(LoggerConfigTest.class.getName());
  }

  @Test
  public void testClassAndPackageNaming() {
    // Simple class/package naming.
    assertThat(LoggerConfig.getConfig(LoggerConfigTest.class).getName())
        .isEqualTo(LoggerConfigTest.class.getName());
    assertThat(LoggerConfig.getPackageConfig(LoggerConfigTest.class).getName())
        .isEqualTo(LoggerConfigTest.class.getPackage().getName());

    // Check that package name is the parent of the class config.
    assertThat(LoggerConfig.getConfig(LoggerConfigTest.class).getParent().getName())
        .isEqualTo(LoggerConfig.getPackageConfig(LoggerConfigTest.class).getName());
  }

  @Test
  public void testUnderlyingLogger() {
    LoggerConfig config = LoggerConfig.getConfig(LoggerConfigTest.class);
    Handler dummyHandler = new Handler() {
      @Override public void publish(LogRecord record) {}
      @Override public void flush() {}
      @Override public void close() throws SecurityException {}
    };
    try {
      config.addHandler(dummyHandler);
      Logger jdkLogger = Logger.getLogger(LoggerConfigTest.class.getName());
      assertThat(jdkLogger.getHandlers()).asList().contains(dummyHandler);
    } finally {
      config.removeHandler(dummyHandler);
    }
  }

  @Test
  public void memberClass_byLogger() {
    LoggerConfig config = LoggerConfig.of(MemberClass.logger);
    assertThat(config.getName()).isEqualTo(MemberClass.class.getCanonicalName());
  }

  @Test
  public void memberClass_byClass() {
    LoggerConfig config = LoggerConfig.getConfig(MemberClass.class);
    assertThat(config.getName()).isEqualTo(MemberClass.class.getCanonicalName());
  }
}
