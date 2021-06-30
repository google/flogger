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

package com.google.common.flogger.backend.system;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform.LogCallerFinder;
import com.google.common.flogger.context.ContextDataProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * These tests check that the internal implementation of the configured platform "plugins" works as
 * expected, but it doesn't really test the singleton behaviour, since the precise platform loaded
 * at runtime can vary in details.
 */
@RunWith(JUnit4.class)
public class DefaultPlatformTest {
  private static final class FakeDefaultPlatform extends DefaultPlatform {
    FakeDefaultPlatform(
        BackendFactory factory,
        ContextDataProvider context,
        Clock clock,
        LogCallerFinder callerFinder) {
      super(factory, context, clock, callerFinder);
    }

    @Override
    protected LogCallerFinder getCallerFinderImpl() {
      return super.getCallerFinderImpl();
    }

    @Override
    protected LoggerBackend getBackendImpl(String className) {
      return super.getBackendImpl(className);
    }

    @Override
    protected long getCurrentTimeNanosImpl() {
      return super.getCurrentTimeNanosImpl();
    }
  }

  @Mock BackendFactory mockBackendFactory;
  @Mock ContextDataProvider mockContext;
  @Mock Clock mockClock;
  @Mock LogCallerFinder mockCallerFinder;

  // The fake platform doesn't attempt to look up any real services, they are just injected mocks.
  private FakeDefaultPlatform platform;

  @Before
  public void initializeMocks() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(mockBackendFactory.toString()).thenReturn("Mock Backend Factory");
    Mockito.when(mockContext.toString()).thenReturn("Mock Logging Context");
    Mockito.when(mockClock.toString()).thenReturn("Mock Clock");
    Mockito.when(mockCallerFinder.toString()).thenReturn("Mock Caller Finder");
    platform =
        new FakeDefaultPlatform(mockBackendFactory, mockContext, mockClock, mockCallerFinder);
  }

  @Test
  public void testClock() {
    Mockito.when(mockClock.getCurrentTimeNanos()).thenReturn(123456789000L);
    assertThat(platform.getCurrentTimeNanosImpl()).isEqualTo(123456789000L);
  }

  @Test
  public void testBackendFactory() {
    LoggerBackend mockBackend = Mockito.mock(LoggerBackend.class);
    Mockito.when(mockBackendFactory.create("logger.name")).thenReturn(mockBackend);
    assertThat(platform.getBackendImpl("logger.name")).isEqualTo(mockBackend);
  }

  @Test
  public void testContextProvider() {
    assertThat(platform.getContextDataProviderImpl()).isSameInstanceAs(mockContext);
  }

  @Test
  public void testLogCallerFinder() {
    assertThat(platform.getCallerFinderImpl()).isSameInstanceAs(mockCallerFinder);
  }

  @Test
  public void testConfigString() {
    assertThat(platform.getConfigInfoImpl()).contains(DefaultPlatform.class.getName());
    assertThat(platform.getConfigInfoImpl()).contains("Clock: Mock Clock");
    assertThat(platform.getConfigInfoImpl()).contains("BackendFactory: Mock Backend Factory");
    assertThat(platform.getConfigInfoImpl()).contains("ContextDataProvider: Mock Logging Context");
    assertThat(platform.getConfigInfoImpl()).contains("LogCallerFinder: Mock Caller Finder");
  }
}
