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

import com.google.common.flogger.backend.LogData;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the abstract logger base-class which don't rely on any specific logging details. */
@RunWith(JUnit4.class)
public class AbstractBackendTest {

  private static class TestBackend extends AbstractBackend {
    // Faked forcing logger (because we don't get our test loggers from the LogManager).
    private class ForcingLogger extends Logger {
      ForcingLogger(Logger parent) {
        super(parent.getName() + ".__forced__", null);
        setParent(parent);
      }

      @Override
      public void log(LogRecord record) {
        wasForced = true;
        super.log(record);
      }
    }

    private boolean wasForced = false;

    TestBackend(Logger logger) {
      super(logger);
    }

    @Override
    public void log(LogData data) {
      // LogData tests are in sub-class tests.
    }

    @Override
    public void handleError(RuntimeException error, LogData badData) {
      // Because log() never tries to format anything, this can never be called.
      throw new UnsupportedOperationException();
    }

    // Normally the forcing logger is obtained from the LogManager (so we get the sub-class
    // implementation), but in tests the Logger used is an explicit subclass that's not in the
    // LogManager hierarchy, so we have to make an explicit child logger here too.
    @Override
    Logger getForcingLogger(final Logger parent) {
      return new ForcingLogger(parent);
    }

    void assertUsedForcingLogger(boolean expectForced) {
      assertThat(wasForced).isEqualTo(expectForced);
      wasForced = false;
    }
  }

  private static final class TestLogger extends Logger {
    private String captured = null;
    private String published = null;

    TestLogger(String name, Level level) {
      super(name, null);
      setLevel(level);
      addHandler(new Handler() {
        @Override public void publish(LogRecord record) {
          published = record.getMessage();
        }

        @Override public void close() {}

        @Override public void flush() {}
      });
    }

    @Override
    public void log(LogRecord record) {
      captured = record.getMessage();
      super.log(record);
    }

    void assertLogged(String expectedLogMessage) {
      assertThat(captured).isEqualTo(expectedLogMessage);
      captured = null;
    }

    void assertPublished(String expectedLogMessage) {
      assertThat(published).isEqualTo(expectedLogMessage);
      published = null;
    }
  }

  @Test
  public void testIsLoggable() {
    Logger logger = new TestLogger("unused", Level.INFO);
    AbstractBackend backend = new TestBackend(logger);
    assertThat(backend.isLoggable(Level.INFO)).isTrue();
    assertThat(backend.isLoggable(Level.FINE)).isFalse();
  }

  @Test
  public void testUnforcedLoggingCallsLoggerDirectly() {
    TestLogger logger = new TestLogger("loggy.mclogface", Level.INFO);
    TestBackend backend = new TestBackend(logger);

    backend.log(new LogRecord(Level.INFO, "Enabled"), false /* unforced */);
    logger.assertLogged("Enabled");
    logger.assertPublished("Enabled");
    backend.assertUsedForcingLogger(false);

    backend.log(new LogRecord(Level.FINE, "Disabled"), false /* unforced */);
    logger.assertLogged("Disabled");
    logger.assertPublished(null);
    backend.assertUsedForcingLogger(false);
  }

  @Test
  public void testForcedLoggingBypassesLoggerIfNeeded() {
    TestLogger logger = new TestLogger("loggy.mclogface", Level.INFO);
    TestBackend backend = new TestBackend(logger);

    // Forced and unforced logging is treated the same if it's enabled.
    backend.log(new LogRecord(Level.INFO, "Enabled and Forced"), true /* forced */);
    logger.assertLogged("Enabled and Forced");
    logger.assertPublished("Enabled and Forced");
    backend.assertUsedForcingLogger(false);

    // Forced logging at a level that's disabled must call the forcing logger instead.
    backend.log(new LogRecord(Level.FINE, "Disabled and Forced"), true /* forced */);
    logger.assertLogged(null);
    logger.assertPublished("Disabled and Forced");
    backend.assertUsedForcingLogger(true);
  }
}
