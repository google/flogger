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
import com.google.common.flogger.backend.Tags;
import java.util.logging.Level;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/**
 * These tests check that the internal implementation of the configured platform "plugins" works as
 * expected, but it doesn't really test the singleton behaviour, since the precise platform loaded
 * at runtime can vary in details.
 */
@RunWith(JUnit4.class)
public class DefaultPlatformTest {

  private static final Tags TEST_TAGS = Tags.builder().addTag("test").build();

  private static final class FakeDefaultPlatform extends DefaultPlatform {
    Clock mockClock;
    BackendFactory mockBackendFactory;
    LoggingContext mockContext;
    LogCallerFinder mockCallerFinder;

    @Override
    protected void configure(Configuration config) {
      super.configure(config);

      // Set the fields here since this is called back from our parent's constructor before our
      // constructor body has been entered (ie, earlier than normal field initialization).
      this.mockClock = Mockito.mock(Clock.class);
      this.mockBackendFactory=  Mockito.mock(BackendFactory.class);
      this.mockContext = Mockito.mock(LoggingContext.class);
      this.mockCallerFinder = Mockito.mock(LogCallerFinder.class);

      Mockito.when(mockClock.toString()).thenReturn("Mock Clock");
      Mockito.when(mockBackendFactory.toString()).thenReturn("Mock Backend Factory");
      Mockito.when(mockContext.toString()).thenReturn("Mock Logging Context");
      Mockito.when(mockCallerFinder.toString()).thenReturn("Mock Caller Finder");

      config.setClock(mockClock);
      config.setBackendFactory(mockBackendFactory);
      config.setLoggingContext(mockContext);
      config.setCallerFinder(mockCallerFinder);
    }
  }

  @Test
  public void testClock() {
    FakeDefaultPlatform platform = new FakeDefaultPlatform();
    Mockito.when(platform.mockClock.getCurrentTimeNanos()).thenReturn(123456789000L);
    assertThat(platform.getCurrentTimeNanosImpl()).isEqualTo(123456789000L);
  }

  @Test
  public void testBackendFactory() {
    FakeDefaultPlatform platform = new FakeDefaultPlatform();
    LoggerBackend mockBackend = Mockito.mock(LoggerBackend.class);
    Mockito.when(platform.mockBackendFactory.create("logger.name")).thenReturn(mockBackend);
    assertThat(platform.getBackendImpl("logger.name")).isEqualTo(mockBackend);
  }

  @Test
  public void testForcedLogging() {
    FakeDefaultPlatform platform = new FakeDefaultPlatform();
    Mockito.when(platform.mockContext.shouldForceLogging("logger.name", Level.INFO, false))
        .thenReturn(true);
    assertThat(platform.shouldForceLoggingImpl("logger.name", Level.INFO, false)).isTrue();
    assertThat(platform.shouldForceLoggingImpl("logger.other.name", Level.INFO, false)).isFalse();
  }

  @Test
  public void testInjectedTags() {
    FakeDefaultPlatform platform = new FakeDefaultPlatform();
    Mockito.when(platform.mockContext.getTags()).thenReturn(TEST_TAGS);
    assertThat(platform.getInjectedTagsImpl()).isEqualTo(TEST_TAGS);
  }

  @Test
  public void testLogCallerFinder() {
    FakeDefaultPlatform platform = new FakeDefaultPlatform();
    assertThat(platform.getCallerFinderImpl()).isEqualTo(platform.mockCallerFinder);
  }

  @Test
  public void testConfigString() {
    FakeDefaultPlatform platform = new FakeDefaultPlatform();
    assertThat(platform.getConfigInfoImpl()).contains(FakeDefaultPlatform.class.getName());
    assertThat(platform.getConfigInfoImpl()).contains("Clock: \"Mock Clock\"");
    assertThat(platform.getConfigInfoImpl()).contains("BackendFactory: \"Mock Backend Factory\"");
    assertThat(platform.getConfigInfoImpl()).contains("LoggingContext: \"Mock Logging Context\"");
    assertThat(platform.getConfigInfoImpl()).contains("LogCallerFinder: \"Mock Caller Finder\"");
  }
}
