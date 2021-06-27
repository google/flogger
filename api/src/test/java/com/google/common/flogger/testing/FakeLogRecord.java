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

package com.google.common.flogger.testing;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.system.AbstractLogRecord;

/**
 * A fake Flogger based {@link java.util.logging.LogRecord} which extends {@link AbstractLogRecord}
 * and can be used to test JDK based log handlers with Flogger behavior.
 */
public final class FakeLogRecord extends AbstractLogRecord {
  /**
   * Returns a log record for the given log site data. Use {@link FakeLogData} to easily generate
   * representative {@code LogData} instances for testing.
   *
   * <p>Note that this API doesn't accept additional context metadata since it is always possible to
   * just add all necessary metadata to a {@code FakeLogData} instance and pass that in. Tests for
   * {@code LogRecord} behavior should never need to differentiate log site vs contextual metadata.
   */
  public static FakeLogRecord of(LogData logData) {
    return new FakeLogRecord(logData);
  }

  private FakeLogRecord(LogData logData) {
    super(logData, Metadata.empty());
    setThrown(getMetadataProcessor().getSingleValue(LogContext.Key.LOG_CAUSE));
  }
}
