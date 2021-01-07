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

import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.testing.FakeLoggerBackend;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.flogger.util.Checks.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class EventAggregatorTest {

  private EventAggregator create(String name, int capacity, FakeLoggerBackend backend){
    FluentAggregatedLogger logger = new FluentAggregatedLogger(backend);
    LogSite logSite = checkNotNull(Platform.getCallerFinder().findLogSite(FluentAggregatedLogger.class, 0),
            "logger backend must not return a null LogSite");
    ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);

    return new EventAggregator(name, logger, logSite, pool, capacity);
  }

  @Test
  public void testAdd() throws InterruptedException {
    String name = "test";
    FakeLoggerBackend backend = new FakeLoggerBackend();
    EventAggregator eventAggregator = create(name, 2, backend);

    assertThat(eventAggregator.getName()).isEqualTo(name);

    eventAggregator.add("event1","hello");
    eventAggregator.add("event2","world");
    eventAggregator.add("event3","flogger");

    Thread.sleep(3); //wait for async flush thread to finish
    eventAggregator.flush(0);

    String message1 = "test\n" +
            "event1:hello | event2:world | \n" +
            "\n" +
            "total: 2";
    backend.assertLogged(0).hasMessage(message1);

    String message2 = "test\n" +
            "event3:flogger | \n" +
            "\n" +
            "total: 1";
    backend.assertLogged(1).hasMessage(message2);
  }

  @Test
  public void testHaveData() {
    String name = "test";
    FakeLoggerBackend backend = new FakeLoggerBackend();
    EventAggregator eventAggregator = create(name, 2, backend);

    eventAggregator.add("event1","hello");
    assertThat(eventAggregator.haveData()).isEqualTo(1);

    eventAggregator.add("event2","world");
    assertThat(eventAggregator.haveData()).isEqualTo(2);

    eventAggregator.add("event3","flogger");
    assertThat(eventAggregator.haveData()).isEqualTo(1);
    //Assert.assertEquals(eventAggregator.haveData(),3);

    eventAggregator.flush(0);
    assertThat(eventAggregator.haveData()).isEqualTo(0);

  }
}
