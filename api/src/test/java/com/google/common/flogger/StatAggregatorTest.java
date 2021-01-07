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

import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.testing.FakeLoggerBackend;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.flogger.util.Checks.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class StatAggregatorTest {

  private StatAggregator create(String name, int capacity, FakeLoggerBackend backend){
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    LogSite logSite = checkNotNull(Platform.getCallerFinder().findLogSite(FluentAggregatedLogger.class, 0),
            "logger backend must not return a null LogSite");
    ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);

    return new StatAggregator(name, logger, logSite, pool, capacity);
  }

  @Test
  public void testWithSampleRate() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    StatAggregator statAggregator = create("test", 3, backend);

    // Test lower bound
    try {
      statAggregator = statAggregator.withSampleRate(-1);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e){
    }

    try {
      statAggregator = statAggregator.withSampleRate(0);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e){
    }

    statAggregator = statAggregator.withSampleRate(1);
    statAggregator.add(10);
    statAggregator.add(10);
    assertThat(statAggregator.valueList.size()).isEqualTo(2);

    statAggregator.flush(0); //Clear value list
    statAggregator = statAggregator.withSampleRate(2);

    statAggregator.add(10);
    assertThat(statAggregator.valueList.size()).isEqualTo(0);
    assertThat(statAggregator.valueList.size()).isEqualTo(0);

    statAggregator.add(10);
    assertThat(statAggregator.valueList.size()).isEqualTo(1);

    statAggregator.add(10);
    assertThat(statAggregator.valueList.size()).isEqualTo(1);

    statAggregator.add(10);
    assertThat(statAggregator.valueList.size()).isEqualTo(2);

    // Test upper bound
    statAggregator = statAggregator.withSampleRate(999).withSampleRate(1000);
    assertThat(statAggregator.getSampleRate()).isEqualTo(1000);

    try {
      statAggregator = statAggregator.withSampleRate(1001);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e){
    }
  }

  @Test
  public void testWithUnit() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    StatAggregator statAggregator = create("test", 3, backend);

    assertThat(statAggregator.getUnit()).isNull();

    String unit = "ms";
    statAggregator = statAggregator.withUnit(unit);
    assertThat(statAggregator.getUnit()).isEqualTo(unit);

    statAggregator.add(10);
    statAggregator.flush(0);

    backend.assertLogged(0).metadata().containsUniqueEntry(StatAggregator.Key.UNIT_STRING, unit);
  }

  @Test
  public void testSample() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    StatAggregator statAggregator = create("test", 3, backend);

    assertThat(statAggregator.sample()).isTrue();
    assertThat(statAggregator.sample()).isTrue();
    assertThat(statAggregator.sample()).isTrue();

    statAggregator = statAggregator.withSampleRate(2);
    assertThat(statAggregator.sample()).isFalse();
    assertThat(statAggregator.sample()).isTrue();
    assertThat(statAggregator.sample()).isFalse();
    assertThat(statAggregator.sample()).isTrue();
    assertThat(statAggregator.sample()).isFalse();
  }

  @Test
  public void testHaveData() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    StatAggregator statAggregator = create("test", 3, backend);

    assertThat(statAggregator.haveData()).isEqualTo(0);
    statAggregator.add(10);
    assertThat(statAggregator.haveData()).isEqualTo(1);

    statAggregator.flush(0);
    assertThat(statAggregator.haveData()).isEqualTo(0);
  }

  @Test
  public void testAdd() throws InterruptedException {
    String name = "test";
    FakeLoggerBackend backend = new FakeLoggerBackend();
    StatAggregator statAggregator = create(name, 4, backend);

    assertThat(statAggregator.getName()).isEqualTo(name);

    // Test value list is full
    statAggregator = statAggregator.withNumberWindow(4);
    for(int i = 0; i < 5; i++) {
      statAggregator.add(10);
    }
    Thread.sleep(10); //wait for async flush finished.
    String message = "test\n" +
            "min:10, max:10, total:40, count:4, avg:10.0.";
    backend.assertLogged(0).hasMessage(message);

    // Test flush based on number window
    statAggregator = statAggregator.withNumberWindow(3);
    statAggregator.add(10);
    statAggregator.add(10);
    Thread.sleep(10); // Wait for async flush finished.

    message = "test\n" +
            "min:10, max:10, total:30, count:3, avg:10.0.";
    backend.assertLogged(1).hasMessage(message);
  }
}
