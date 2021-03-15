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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.backend.Metadata;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides per log site state for stateful fluent logging operations (e.g. rate limiting).
 *
 * <p>A log site map allows a logging API to efficiently, and safely, retrieve mutable log site
 * state. This state can then be updated according to the current log statement.
 *
 * <p>Note that values held in this map are expected to be mutable and must still be thread safe
 * themselves (the map protects only from concurrent lookup, not concurrent modification of the
 * state itself). It is also strongly advised that all implementations of log site state avoid using
 * locking (e.g. "synchronized" data structres) due to the risk of causing not trivial and
 * potentially harmful thread contention bottlenecks during logging.
 *
 * <p>This class is intended only for use by fluent logging APIs (subclasses of {@link LogContext}
 * and only used in the {@link LogContext#postProcess(LogSiteKey)} method, which supplies the key
 * appropriate for the current log statement.
 *
 * @param <V> The value type in the map.
 */
public abstract class LogSiteMap<V> {
  private final ConcurrentHashMap<LogSiteKey, V> concurrentMap =
      new ConcurrentHashMap<LogSiteKey, V>();

  protected LogSiteMap() {}

  /**
   * Implemented by subclasses to provide a new value for a newly added keys. This value is mapped
   * to the key and cannot be replaced, so it is expected to be mutable and must be thread safe.
   * All values in a {@code LogSiteMap} are expected to be the same type and have the same initial
   * state.
   */
  protected abstract V initialValue();

  // This method exists only for testing. Do not make this public.
  boolean contains(LogSiteKey key) {
    return concurrentMap.containsKey(key);
  }

  /**
   * Returns the mutable, thread safe, log site state for the given key to be read or updated during
   * the {@link LogContext#postProcess(LogSiteKey)} method.
   *
   * <p>Note that due to the possibility of log site key specialization, there may be more than one
   * value in the map for any given log site. This is intended and allows for things like per scope
   * rate limiting.
   */
  public final V get(LogSiteKey key, Metadata metadata) {
    V value = concurrentMap.get(key);
    if (value != null) {
      return value;
    }
    // Many threads can get here concurrently and attempt to add an initial value.
    value = checkNotNull(initialValue(), "initial map value");
    V race = concurrentMap.putIfAbsent(key, value);
    if (race != null) {
      return race;
    }
    // Only one thread gets here for each log site key added to this map.
    addRemovalHook(key, metadata);
    return value;
  }

  private void addRemovalHook(final LogSiteKey key, Metadata metadata) {
    Runnable removalHook = null;
    for (int i = 0, count = metadata.size(); i < count; i++) {
      if (!LogContext.Key.LOG_SITE_GROUPING_KEY.equals(metadata.getKey(i))) {
        continue;
      }
      Object groupByKey = metadata.getValue(i);
      if (!(groupByKey instanceof LoggingScope)) {
        continue;
      }
      if (removalHook == null) {
        // Non-static inner class references the outer LogSiteMap.
        removalHook = new Runnable() {
          @Override
          public void run() {
            concurrentMap.remove(key);
          }
        };
      }
      ((LoggingScope) groupByKey).onClose(removalHook);
    }
  }
}
