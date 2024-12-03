/*
 * Copyright (C) 2024 The Flogger Authors.
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

import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogSiteTest {

  @Test
  public void injectedLogSite_internalVsExternalName() {
    String className = "com.google.common.flogger.LogSiteTest";
    String internalClassName = "com/google/common/flogger/LogSiteTest";

    // Using the same class, but represented by the internal vs external name, should
    // be considered equal.
    new EqualsTester()
        .addEqualityGroup(logSite(className), logSite(internalClassName))
        .addEqualityGroup(logSite(className + "2"), logSite(internalClassName + "2"))
        .addEqualityGroup(logSite(className + "3"), logSite(internalClassName + "3"))
        .testEquals();

    // The public "className()" method will be the same
    assertThat(logSite(className).getClassName()).isEqualTo(className);
    assertThat(logSite(internalClassName).getClassName()).isEqualTo(className);
  }

  @Test
  public void injectedLogSite_distinguishesWhenDifferentOnPackageSeparator() {
    String className = "com.google.common.flogger.LogSiteTest";
    String sneakyClassName = "combgoogle/common/flogger/LogSiteTest";

    new EqualsTester()
        .addEqualityGroup(logSite(className))
        .addEqualityGroup(logSite(sneakyClassName))
        .testEquals();
  }

  // This technically passes, but is not what we want, since the botched name isn't a real class
  // identifier. The complexity of tracking the separator is not worth it, however.
  @Test
  public void injectedLogSite_invalidClassName_stillValid() {
    String className = "com.google.common.flogger.LogSiteTest";
    String internalClassName = "com/google/common/flogger/LogSiteTest";
    String botchedClassName = "com.google/common.flogger/LogSiteTest";

    new EqualsTester()
        .addEqualityGroup(logSite(className), logSite(internalClassName), logSite(botchedClassName))
        .testEquals();
  }

  @SuppressWarnings("deprecation") // Intentionally calling injectedLogSite to test it.
  private static LogSite logSite(String className) {
    return LogSite.injectedLogSite(className, "someMethod", 42, "MyFile.java");
  }
}
