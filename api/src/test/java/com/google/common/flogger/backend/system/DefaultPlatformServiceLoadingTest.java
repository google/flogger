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

package com.google.common.flogger.backend.system;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.service.AutoService;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.ScopedLoggingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests how {@code DefaultPlatform} loads services from the classpath. */
@RunWith(JUnit4.class)
public final class DefaultPlatformServiceLoadingTest {

  @Test
  public void testConfigString() {
    DefaultPlatform platform = new DefaultPlatform();
    assertThat(platform.getConfigInfoImpl()).contains(DefaultPlatform.class.getName());
    assertThat(platform.getConfigInfoImpl()).contains("Clock: Default millisecond precision clock");
    assertThat(platform.getConfigInfoImpl()).contains("BackendFactory: TestBackendFactoryService");
    assertThat(platform.getConfigInfoImpl())
        .contains("ContextDataProvider: TestContextDataProviderService");
    assertThat(platform.getConfigInfoImpl())
        .contains("LogCallerFinder: Default stack-based caller finder");
  }

  @AutoService(BackendFactory.class)
  public static final class TestBackendFactoryService extends BackendFactory {
    @Override
    public LoggerBackend create(String loggingClassName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "TestBackendFactoryService";
    }
  }

  @AutoService(ContextDataProvider.class)
  public static final class TestContextDataProviderService extends ContextDataProvider {
    @Override
    public ScopedLoggingContext getContextApiSingleton() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "TestContextDataProviderService";
    }
  }
}
