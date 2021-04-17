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

import com.google.common.flogger.MetadataKey;
import org.apache.logging.log4j.util.StringMap;

final class Log4j2KeyValueHandler implements MetadataKey.KeyValueHandler {

    private final StringMap contextData;

    public Log4j2KeyValueHandler(StringMap contextData) {
        this.contextData = contextData;
    }

    @Override
    public void handle(String key, Object value) {
        if (value == null) {
            return;
        }

        contextData.putValue(key, value);
    }
}
