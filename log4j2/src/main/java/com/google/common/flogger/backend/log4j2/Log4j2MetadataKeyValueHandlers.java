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

 package com.google.common.flogger.backend.log4j2;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.MetadataHandler;

import java.util.Iterator;

/** 
 * A helper class providing the default callbacks and handlers for processing metadata as key/value
 * pairs in order to use them with Log4j2.
 */
final class Log4j2MetadataKeyValueHandlers {
    private static final MetadataHandler.ValueHandler<Object, Log4j2KeyValueHandler> EMIT_METADATA =
            new MetadataHandler.ValueHandler<Object, Log4j2KeyValueHandler>() {
                @Override
                public void handle(MetadataKey<Object> key, Object value, Log4j2KeyValueHandler kvh) {
                    key.emit(value, kvh);
                }
            };

    private static final MetadataHandler.RepeatedValueHandler<Object, Log4j2KeyValueHandler> EMIT_REPEATED_METADATA =
            new MetadataHandler.RepeatedValueHandler<Object, Log4j2KeyValueHandler>() {
                // Passing a list is important to delegate the formatting to the layout class.
                // At this point we do not know the target format, e.g. PatternLayout vs JsonLayout
                @Override
                public void handle(MetadataKey<Object> key, Iterator<Object> values, Log4j2KeyValueHandler kvh) {
                    kvh.handle(key.getLabel(), ImmutableList.copyOf(values));
                }
            };

    /** Returns a singleton value handler which dispatches metadata to a {@link Log4j2KeyValueHandler}. */
    public static MetadataHandler.ValueHandler<Object, Log4j2KeyValueHandler> getDefaultValueHandler() {
        return EMIT_METADATA;
    }

    /** Returns a singleton value handler which dispatches metadata to a {@link Log4j2KeyValueHandler}. */
    public static MetadataHandler.RepeatedValueHandler<Object, Log4j2KeyValueHandler> getDefaultRepeatedValueHandler() {
        return EMIT_REPEATED_METADATA;
    }

    private Log4j2MetadataKeyValueHandlers() {
    }
}