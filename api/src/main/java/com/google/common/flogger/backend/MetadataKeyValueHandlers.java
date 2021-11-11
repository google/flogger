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

package com.google.common.flogger.backend;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.MetadataKey.KeyValueHandler;
import com.google.common.flogger.backend.MetadataHandler.RepeatedValueHandler;
import com.google.common.flogger.backend.MetadataHandler.ValueHandler;
import java.util.Iterator;
import java.util.Set;

/**
 * A helper class providing the default callbacks and handlers for processing metadata as key/value
 * pairs. It is expected that most text-based logger backends will format unknown metadata using the
 * handlers from this class.
 */
public final class MetadataKeyValueHandlers {
  private static final ValueHandler<Object, KeyValueHandler> EMIT_METADATA =
      new ValueHandler<Object, KeyValueHandler>() {
        @Override
        public void handle(MetadataKey<Object> key, Object value, KeyValueHandler kvf) {
          key.safeEmit(value, kvf);
        }
      };

  private static final RepeatedValueHandler<Object, KeyValueHandler> EMIT_REPEATED_METADATA =
      new RepeatedValueHandler<Object, KeyValueHandler>() {
        @Override
        public void handle(MetadataKey<Object> key, Iterator<Object> value, KeyValueHandler kvf) {
          key.safeEmitRepeated(value, kvf);
        }
      };

  /** Returns a singleton value handler which dispatches metadata to a {@link KeyValueHandler}. */
  public static ValueHandler<Object, KeyValueHandler> getDefaultValueHandler() {
    return EMIT_METADATA;
  }

  /** Returns a singleton value handler which dispatches metadata to a {@link KeyValueHandler}. */
  public static RepeatedValueHandler<Object, KeyValueHandler> getDefaultRepeatedValueHandler() {
    return EMIT_REPEATED_METADATA;
  }

  /**
   * Returns a new {@link MetadataHandler.Builder} which handles all non-ignored metadata keys by
   * dispatching their values to the key itself. This is convenient for generic metadata processing
   * when used in conjunction with something like {@link KeyValueFormatter}.
   *
   * <p>The returned builder can be built immediately or customized further to handler some keys
   * specially (e.g. allowing keys/values to modify logging behaviour).
   *
   * @return a builder configured with the default key/value handlers and ignored keys.
   */
  public static MetadataHandler.Builder<KeyValueHandler> getDefaultBuilder(
      Set<MetadataKey<?>> ignored) {
    return MetadataHandler.builder(getDefaultValueHandler())
        .setDefaultRepeatedHandler(getDefaultRepeatedValueHandler())
        .ignoring(ignored);
  }

  /**
   * Returns a new {@link MetadataHandler} which handles all non-ignored metadata keys by
   * dispatching their values to the key itself. This is convenient for generic metadata processing
   * when used in conjunction with something like {@link KeyValueFormatter}.
   *
   * @return a handler configured with the default key/value handlers and ignored keys.
   */
  public static MetadataHandler<KeyValueHandler> getDefaultHandler(Set<MetadataKey<?>> ignored) {
    return getDefaultBuilder(ignored).build();
  }

  private MetadataKeyValueHandlers() {}
}
