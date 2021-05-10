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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogSitesTest {
  @Test
  public void testLogSite() {
    assertThat(LogSites.logSite().getMethodName()).isEqualTo("testLogSite");
  }

  @Test
  public void testCallerOf() {
    assertThat(MyLogUtil.getCallerLogSite().getMethodName()).isEqualTo("testCallerOf");
    assertThat(MyLogUtil.getCallerLogSiteWrapped().getMethodName()).isEqualTo("testCallerOf");
  }

  @Test
  public void testCallerOf_notFound() {
    assertThat(LogSites.callerOf(String.class)).isEqualTo(LogSite.INVALID);
  }

  @Test
  public void testCallerOf_from() {
    StackTraceElement e = new StackTraceElement("class", "method", "file", 42);
    LogSite logSite = LogSites.logSiteFrom(e);
    assertThat(logSite.getClassName()).isEqualTo(e.getClassName());
    assertThat(logSite.getMethodName()).isEqualTo(e.getMethodName());
    assertThat(logSite.getFileName()).isEqualTo(e.getFileName());
    assertThat(logSite.getLineNumber()).isEqualTo(e.getLineNumber());
  }


  private static class MyLogUtil {
    static LogSite getCallerLogSite() {
      return LogSites.callerOf(MyLogUtil.class);
    }

    static LogSite getCallerLogSiteWrapped() {
      return getCallerLogSite();
    }
  }
}
