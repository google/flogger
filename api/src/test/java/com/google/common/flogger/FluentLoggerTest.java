/*
 * Copyright (C) 2014 The Flogger Authors.
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

import com.google.common.flogger.FluentLogger.Context;
import com.google.common.flogger.testing.FakeLoggerBackend;
import java.util.logging.Level;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Fluent loggers are typically very simple classes whose only real responsibility is as a factory
 * for a specific API implementation. As such it needs very few tests itself.
 *
 * See LogContextTest.java for the vast majority of tests related to base logging behaviour.
 */
@RunWith(JUnit4.class)
public class FluentLoggerTest {
  @Test
  public void testCreate() {
    FluentLogger logger = FluentLogger.forEnclosingClass();
    assertThat(logger.getName()).isEqualTo(FluentLoggerTest.class.getName());

    // Note that this one-to-one binding of loggers and backends is not strictly necessary and in
    // future it's plausible that a configured backend factory might return backends shared with
    // many loggers. In that situation, the logger name must still be the enclosing class name
    // (held separately by the logger itself) while the backend name could differ.
    assertThat(logger.getBackend().getLoggerName()).isEqualTo(FluentLoggerTest.class.getName());
  }

  @Test
  public void testNoOp() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentLogger logger = new FluentLogger(backend);
    backend.setLevel(Level.INFO);

    // Down to and including the configured log level are not the no-op instance.
    assertThat(logger.atSevere()).isNotSameInstanceAs(FluentLogger.NO_OP);
    assertThat(logger.atSevere()).isInstanceOf(Context.class);
    assertThat(logger.atWarning()).isNotSameInstanceAs(FluentLogger.NO_OP);
    assertThat(logger.atWarning()).isInstanceOf(Context.class);
    assertThat(logger.atInfo()).isNotSameInstanceAs(FluentLogger.NO_OP);
    assertThat(logger.atInfo()).isInstanceOf(Context.class);

    // Below the configured log level you only get the singleton no-op instance.
    assertThat(logger.atFine()).isSameInstanceAs(FluentLogger.NO_OP);
    assertThat(logger.atFiner()).isSameInstanceAs(FluentLogger.NO_OP);
    assertThat(logger.atFinest()).isSameInstanceAs(FluentLogger.NO_OP);

    // Just verify that logs below the current log level are discarded.
    logger.atFine().log("DISCARDED");
    logger.atFiner().log("DISCARDED");
    logger.atFinest().log("DISCARDED");
    assertThat(backend.getLoggedCount()).isEqualTo(0);

    // But those at or above are passed to the backend.
    logger.atInfo().log("LOGGED");
    assertThat(backend.getLoggedCount()).isEqualTo(1);

    backend.setLevel(Level.OFF);
    assertThat(logger.atSevere()).isSameInstanceAs(FluentLogger.NO_OP);

    backend.setLevel(Level.ALL);
    assertThat(logger.atFinest()).isNotSameInstanceAs(FluentLogger.NO_OP);
  }
}
