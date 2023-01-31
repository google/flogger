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

import com.google.common.flogger.LogContext;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import java.util.logging.LogRecord;

/**
 * An eagerly evaluating {@link LogRecord} which is created by the Fluent Logger frontend and can be
 * passed to a normal log {@link java.util.logging.Handler} instance for output.
 */
public final class SimpleLogRecord extends AbstractLogRecord {
  /** Creates a {@link SimpleLogRecord} for a normal log statement from the given data. */
  public static SimpleLogRecord create(LogData data, Metadata scope) {
    return new SimpleLogRecord(data, scope);
  }

  /** @deprecated Use create(LogData data, Metadata scope) and pass scoped metadata in. */
  @Deprecated
  public static SimpleLogRecord create(LogData data) {
    return create(data, Metadata.empty());
  }

  /** Creates a {@link SimpleLogRecord} in the case of an error during logging. */
  public static SimpleLogRecord error(RuntimeException error, LogData data, Metadata scope) {
    return new SimpleLogRecord(error, data, scope);
  }

  /** @deprecated Use error(LogData data, Metadata scope) and pass scoped metadata in. */
  @Deprecated
  public static SimpleLogRecord error(RuntimeException error, LogData data) {
    return error(error, data, Metadata.empty());
  }

  private SimpleLogRecord(LogData data, Metadata scope) {
    super(data, scope);
    setThrown(getMetadataProcessor().getSingleValue(LogContext.Key.LOG_CAUSE));

    // Calling getMessage() formats and caches the formatted message in the AbstractLogRecord.
    //
    // IMPORTANT: Conceptually there's no need to format the log message here, since backends can
    // choose to format messages in different ways or log structurally, so it's not obviously a
    // win to format things here first. Formatting would otherwise be done by AbstractLogRecord
    // when getMessage() is called, and the results cached; so the only effect of being "lazy"
    // should be that formatting (and thus calls to the toString() methods of arguments) happens
    // later in the same log statement.
    //
    // However ... due to bad use of locking in core JDK log handler classes, any lazy formatting
    // of log arguments (i.e. in the Handler's "publish()" method) can be done with locks held,
    // and thus risks deadlock. We can mitigate the risk by formatting the message string early
    // (i.e. here). This is wasteful in cases where this message is never needed (e.g. structured
    // logging) but necessary when using many of the common JDK handlers (e.g. StreamHandler,
    // FileHandler etc.) and it's impossible to know which handlers are being used.
    String unused = getMessage();
  }

  private SimpleLogRecord(RuntimeException error, LogData data, Metadata scope) {
    // In the case of an error, the base class handles everything as there's no specific formatting.
    super(error, data, scope);
  }
}
