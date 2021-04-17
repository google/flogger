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

import com.google.common.flogger.backend.MetadataHandler;

final class Log4j2MetadataHandler {
    private static final MetadataHandler<Log4j2KeyValueHandler> DEFAULT_HANDLER = MetadataHandler
            .builder(Log4j2MetadataKeyValueHandlers.getDefaultValueHandler())
            .setDefaultRepeatedHandler(Log4j2MetadataKeyValueHandlers.getDefaultRepeatedValueHandler())
            .build();

    private Log4j2MetadataHandler() {
    }

    /** Returns a singleton metadata handler wich dispatches metadata to a {@link Log4j2KeyValueHandler} */
    public static MetadataHandler<Log4j2KeyValueHandler> getDefaultMetadataHandler() {
        return DEFAULT_HANDLER;
    }
}
