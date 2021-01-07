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

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import com.google.errorprone.annotations.CheckReturnValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.flogger.util.Checks.checkNotNull;

/**
 * The default aggregated logger which uses the default parser and system configured backend.
 * <p>
 * FluentAggregatedLogger will initiate {@link EventAggregator} or {@link StatAggregator}.
 */
@CheckReturnValue
public final class FluentAggregatedLogger extends AbstractLogger {
  //Executor service pool for all aggregated loggers to periodically flush log.
  private static final ScheduledExecutorService pool = Executors.newScheduledThreadPool(8);

  //Cache the AggregatedLogContext instance for multi threads
  private final Map<String, AggregatedLogContext> aggregatorMap = new ConcurrentHashMap<String, AggregatedLogContext>();

  //Visible for unit testing
  protected FluentAggregatedLogger(LoggerBackend backend) {
    super(backend);
  }

  public static FluentAggregatedLogger forEnclosingClass() {
    // NOTE: It is _vital_ that the call to "caller finder" is made directly inside the static
    // factory method. See getCallerFinder() for more information.
    String loggingClass = Platform.getCallerFinder().findLoggingClass(FluentAggregatedLogger.class);
    return new FluentAggregatedLogger(Platform.getBackend(loggingClass));
  }

  /**
   * Get EventAggregator
   *
   * @param name aggregator logger name, should be unique in the same FluentAggregatedLogger scope.
   * @return EventAggregator
   */
  public EventAggregator getEvent(String name) {
    AggregatedLogContext aggregator = aggregatorMap.get(name);
    if (aggregator == null) {
      //Get logsite here for async write data in new Thread
      LogSite logSite = checkNotNull(Platform.getCallerFinder().findLogSite(FluentAggregatedLogger.class, 0),
        "logger backend must not return a null LogSite");
      aggregator = new EventAggregator(name, this, logSite, pool, 1024 * 1024);
      aggregatorMap.putIfAbsent(name, aggregator);

      AggregatedLogContext old = aggregatorMap.putIfAbsent(name, aggregator);

      if (old != null) {
        aggregator = old;
      }
    }

    if (!(aggregator instanceof EventAggregator)) {
      throw new RuntimeException("There is another kind of logger with the same name");
    }

    return (EventAggregator) aggregator;
  }

  /**
   * Get StatAggregator
   *
   * @param name aggregator logger name, should be unique in the same FluentAggregatedLogger scope.
   * @return StatAggregator
   */
  public StatAggregator getStat(String name) {
    AggregatedLogContext aggregator = aggregatorMap.get(name);
    if (aggregator == null) {
      //Get logsite here for async write data in new Thread
      LogSite logSite = checkNotNull(Platform.getCallerFinder().findLogSite(FluentAggregatedLogger.class, 0),
        "logger backend must not return a null LogSite");
      aggregator = new StatAggregator(name, this, logSite, pool, 1024 * 1024);
      AggregatedLogContext old = aggregatorMap.putIfAbsent(name, aggregator);

      if (old != null) {
        aggregator = old;
      }
    }

    if (!(aggregator instanceof StatAggregator)) {
      throw new RuntimeException("There is another kind of logger with the same name: " + name);
    }

    return (StatAggregator) aggregator;
  }
}
