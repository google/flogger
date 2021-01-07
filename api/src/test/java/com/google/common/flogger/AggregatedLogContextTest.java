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

import com.google.common.flogger.testing.FakeLoggerBackend;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class AggregatedLogContextTest {
  final static FluentAggregatedLogger logger2 = FluentAggregatedLogger.forEnclosingClass();

  @Test
  public void testGetName() {
    String name = "test";
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    EventAggregator eventAggregator = logger.getEvent(name);

    assertThat(eventAggregator.getName()).isEqualTo(name);
  }

  @Test
  public void testWithTimeWindow() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    EventAggregator eventAggregator = logger.getEvent("test");

    // Test lower bound
    try {
      eventAggregator.withTimeWindow(-1);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }

    try {
      eventAggregator.withTimeWindow(0);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }

    eventAggregator.withTimeWindow(1).add("timewindow", "1");
    eventAggregator.flush(0);
    backend.assertLogged(0).metadata().containsUniqueEntry(AggregatedLogContext.Key.TIME_WINDOW, 1);

    // Test upper bound
    try {
      eventAggregator.withTimeWindow(3601);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {

    }
    try {
      eventAggregator.withTimeWindow(10000);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    }

    EventAggregator eventAggregator2 = logger.getEvent("test2");
    eventAggregator2.withTimeWindow(3600).add("timewindow", "3600");
    eventAggregator2.flush(0);
    backend.assertLogged(1).metadata().containsUniqueEntry(AggregatedLogContext.Key.TIME_WINDOW, 3600);

    // Test repeatedly set
    eventAggregator.withTimeWindow(2).add("timewindow", "2");
    eventAggregator.flush(0);
    backend.assertLogged(2).metadata().containsUniqueEntry(AggregatedLogContext.Key.TIME_WINDOW, 2);
    try {
      eventAggregator = eventAggregator.start().withTimeWindow(3);
      fail("expected RuntimeException");
    } catch (RuntimeException e) {
    }
  }

  @Test
  public void testWithNumberWindow() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    EventAggregator eventAggregator = logger.getEvent("test");

    // Test lower bound
    try {
      eventAggregator.withNumberWindow(-1);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }

    try {
      eventAggregator.withNumberWindow(0);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }

    eventAggregator.withNumberWindow(1).add("numberwindow", "1");
    eventAggregator.flush(0);
    backend.assertLogged(0).metadata().containsUniqueEntry(AggregatedLogContext.Key.NUMBER_WINDOW, 1);

    // Test upper bound
    try {
      eventAggregator.withNumberWindow(1000 * 1000 + 1);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {

    }
    try {
      eventAggregator.withNumberWindow(1024 * 1024);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    }
    eventAggregator.withNumberWindow(1000 * 1000).add("timewindow", "1000 * 1000");
    eventAggregator.flush(0);
    backend.assertLogged(1).metadata().containsUniqueEntry(AggregatedLogContext.Key.NUMBER_WINDOW, 1000 * 1000);
  }

  @Test
  public void testShouldFlush() throws InterruptedException {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    EventAggregator eventAggregator = logger.getEvent("test");

    assertThat(eventAggregator.shouldFlushByNumber()).isFalse();

    eventAggregator = eventAggregator.withNumberWindow(10);
    for (int i = 0; i < 9; i++) {
      eventAggregator.add("hello", "world");
    }
    assertThat(eventAggregator.shouldFlushByNumber()).isFalse();
    eventAggregator.add("hello", "world");
    assertThat(eventAggregator.shouldFlushByNumber()).isTrue();

    Thread.sleep(10); // Waiting for async flush thread to finish
    eventAggregator.add("hello", "world");
    assertThat(eventAggregator.shouldFlushByNumber()).isFalse();
  }
}
