/*
 * Copyright (C) 2019 The Flogger Authors.
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

package com.google.common.flogger.context;

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * A hierarchical mapping from logger name to {@link Level} used to override the configured log
 * level during debugging. This class is designed to allow efficient (i.e. zero-allocation)
 * resolution of the log level for a given logger.
 *
 * <p>This class is immutable and thread safe.
 */
public final class LogLevelMap {
  /**
   * Builder for log level map which uses type safe class/package keys (but requires that they be
   * present in the JVM at the time the map is created). To set up a {@code LogLevelMap} with only
   * class/package names, use {@link LogLevelMap#create(java.util.Map,Level)} or {@link
   * LogLevelMap#create(java.util.Map)}.
   */
  public static final class Builder {
    private final Map<String, Level> map = new HashMap<String, Level>();
    private Level defaultLevel = Level.OFF;

    private Builder() {}

    private void put(String name, Level level) {
      if (map.put(name, level) != null) {
        throw new IllegalArgumentException("duplicate entry for class/package: " + name);
      }
    }

    /** Adds the given classes at the specified log level. */
    @CanIgnoreReturnValue
    public Builder add(Level level, Class<?>... classes) {
      for (Class<?> cls : classes) {
        put(cls.getName(), level);
      }
      return this;
    }

    /** Adds the given packages at the specified log level. */
    @CanIgnoreReturnValue
    public Builder add(Level level, Package... packages) {
      for (Package pkg : packages) {
        put(pkg.getName(), level);
      }
      return this;
    }

    /** Sets the default log level (use {@link Level#OFF} to disable. */
    @CanIgnoreReturnValue
    public Builder setDefault(Level level) {
      checkNotNull(defaultLevel, "default log level must not be null");
      this.defaultLevel = level;
      return this;
    }

    public LogLevelMap build() {
      return LogLevelMap.create(map, defaultLevel);
    }
  }

  /** Returns a new builder for constructing a {@code LogLevelMap}. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns an empty {@code LogLevelMap} with a single default level which will apply to all
   * loggers.
   */
  public static LogLevelMap create(Level level) {
    return create(Collections.<String, Level>emptyMap(), level);
  }

  /**
   * Returns a {@code LogLevelMap} whose entries correspond to the given map, and with the default
   * value of {@code Level.OFF}. The keys of the map must all be valid dot-separated logger names,
   * and the values cannot be {@code null}.
   */
  public static LogLevelMap create(Map<String, ? extends Level> map) {
    return create(map, Level.OFF);
  }

  /**
   * Returns a {@code LogLevelMap} whose entries correspond to the given map. The keys of the map
   * must all be valid dot-separated logger names, and neither the values, nor the default value,
   * can be {@code null}.
   */
  public static LogLevelMap create(Map<String, ? extends Level> map, Level defaultLevel) {
    checkNotNull(defaultLevel, "default log level must not be null");
    for (Map.Entry<String, ? extends Level> e : map.entrySet()) {
      String name = e.getKey();
      if (name.startsWith(".") || name.endsWith(".") || name.contains("..")) {
        throw new IllegalArgumentException("invalid logger name: " + name);
      }
      if (e.getValue() == null) {
        throw new IllegalArgumentException("log levels must not be null; logger=" + name);
      }
    }
    return new LogLevelMap(map, defaultLevel);
  }

  private final SegmentTrie<Level> trie;

  private LogLevelMap(Map<String, ? extends Level> map, Level defaultLevel) {
    this.trie = SegmentTrie.create(map, '.', defaultLevel);
  }

  /**
   * Returns the log level for the specified logger, matching the {@code loggerName} to an entry in
   * the map, or the nearest parent in the naming hierarchy. If the given {@code loggerName} is
   * invalid, the default value is returned.
   */
  public Level getLevel(String loggerName) {
    return trie.find(loggerName);
  }

  /**
   * Returns the union of this map with the given map. Logging is enabled in the merged map
   * if-and-only-if it was enabled in one of the maps it was created from.
   */
  public LogLevelMap merge(LogLevelMap other) {
    Map<String, Level> thisMap = trie.getEntryMap();
    Map<String, Level> otherMap = other.trie.getEntryMap();

    // HashMap/HashSet is fine because iteration order is unimportant for creating a SegmentTrie.
    Map<String, Level> mergedMap = new HashMap<String, Level>();
    Set<String> allKeys = new HashSet<String>(thisMap.keySet());
    allKeys.addAll(otherMap.keySet());
    for (String key : allKeys) {
      if (!otherMap.containsKey(key)) {
        mergedMap.put(key, thisMap.get(key));
      } else if (!thisMap.containsKey(key)) {
        mergedMap.put(key, otherMap.get(key));
      } else {
        mergedMap.put(key, min(thisMap.get(key), otherMap.get(key)));
      }
    }

    Level defaultLevel = min(trie.getDefaultValue(), other.trie.getDefaultValue());
    return create(mergedMap, defaultLevel);
  }

  private static Level min(Level a, Level b) {
    return a.intValue() <= b.intValue() ? a : b;
  }
}
