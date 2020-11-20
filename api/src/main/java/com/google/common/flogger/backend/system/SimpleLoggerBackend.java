/*
 * Copyright (C) 2012 The Flogger Authors.
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

package com.google.common.flogger.backend.system;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Platform;
import java.util.logging.Logger;

/** A logging backend that uses the {@code java.util.logging} classes to output log statements. */
public class SimpleLoggerBackend extends AbstractBackend {

  public SimpleLoggerBackend(Logger logger) {
    super(logger);
  }

  @Override
  public void log(LogData data) {
    log(SimpleLogRecord.create(data, Platform.getInjectedMetadata()), data.wasForced());
  }

  @Override
  public void handleError(RuntimeException error, LogData badData) {
    log(SimpleLogRecord.error(error, badData, Platform.getInjectedMetadata()), badData.wasForced());
  }
}
