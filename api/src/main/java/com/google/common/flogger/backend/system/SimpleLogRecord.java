/*
 * Copyright (C) 2014 The Flogger Authors.
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
import com.google.common.flogger.backend.SimpleMessageFormatter;
import com.google.common.flogger.backend.SimpleMessageFormatter.SimpleLogHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;

/**
 * An eagerly evaluating {@link LogRecord} which is created by the Fluent Logger frontend and can be
 * passed to a normal log {@link java.util.logging.Handler} instance for output.
 */
public final class SimpleLogRecord extends AbstractLogRecord implements SimpleLogHandler {

  /** Creates a {@link SimpleLogRecord} for a normal log statement from the given data. */
  public static SimpleLogRecord create(LogData data) {
    return new SimpleLogRecord(data);
  }

  /** Creates a {@link SimpleLogRecord} in the case of an error during logging. */
  public static SimpleLogRecord error(RuntimeException error, LogData data) {
    return new SimpleLogRecord(error, data);
  }

  private SimpleLogRecord(LogData data) {
    super(data);
    // TODO(user): Maybe we do want to do formatting on demand.
    // This would avoid formatting when the caller will just get the structured data via the
    // LogData API, or when the record is filtered. However neither of these are happening at the
    // moment and when structured data is required, a different log record should be used.
    SimpleMessageFormatter.format(data, this);
  }

  private SimpleLogRecord(RuntimeException error, LogData data) {
    // In the case of an error, the base class handles everything as there's no specific formatting.
    super(error, data);
  }

  @Override
  public void handleFormattedLogMessage(Level level, String message, @Nullable Throwable thrown) {
    // Ignore the log level as our parent class already set it (that's used in Android).
    setMessage(message);
    setThrown(thrown);
  }
}
