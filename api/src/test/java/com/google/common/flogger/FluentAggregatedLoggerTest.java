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

package com.google.common.flogger;

import com.google.common.flogger.testing.FakeLoggerBackend;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.logging.Level;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

/**
 * FluentAggregatedLogger is typically very simple classes whose only real responsibility is as a factory
 * for a specific API implementation. As such it needs very few tests itself.
 * <p>
 * See AggregatedLogContextTest.java for the vast majority of tests related to base logging behaviour.
 */
@RunWith(JUnit4.class)
public class FluentAggregatedLoggerTest {
  @Test
  public void testCreate() {
    FluentAggregatedLogger logger = FluentAggregatedLogger.forEnclosingClass();
    assertThat(logger.getName()).isEqualTo(FluentAggregatedLoggerTest.class.getName());
    assertThat(logger.getBackend().getLoggerName()).isEqualTo(FluentAggregatedLoggerTest.class.getName());
  }

  @Test
  public void testGetEvent() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    backend.setLevel(Level.INFO);

    // Check name
    String name1 = "event1";
    EventAggregator eventAggregator = logger.getEvent(name1);
    assertThat(eventAggregator.getName()).isEqualTo(name1);
    // Test repeatedly get
    EventAggregator same = logger.getEvent(name1);
    assertThat(eventAggregator).isSameInstanceAs(same);

    //Test different name
    String name2 = "event2";
    EventAggregator eventAggregator2 = logger.getEvent(name2);
    assertThat(eventAggregator2.getName()).isEqualTo(name2);
    assertThat(eventAggregator).isNotSameInstanceAs(eventAggregator2);

    //Test different type aggregator with the same name
    try {
      StatAggregator statAggregator = logger.getStat(name1);
      fail("expected RuntimeException");
    } catch (RuntimeException e) {

    }
  }

  @Test
  public void testGetStat() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    backend.setLevel(Level.INFO);

    // Check name
    String name1 = "stat1";
    StatAggregator statAggregator1 = logger.getStat(name1);
    assertThat(statAggregator1.getName()).isEqualTo(name1);

    // Test repeatedly get
    StatAggregator same = logger.getStat(name1);
    assertThat(statAggregator1).isEqualTo(same);

    //Test different name
    String name2 = "stat2";
    StatAggregator statAggregator2 = logger.getStat(name2);
    assertThat(statAggregator2.getName()).isEqualTo(name2);
    assertThat(statAggregator1).isNotSameInstanceAs(statAggregator2);

    //Test different type aggregator with the same name
    try {
      EventAggregator eventAggregator = logger.getEvent(name1);
      fail("expected RuntimeException");
    } catch (RuntimeException e) {

    }
  }
}
