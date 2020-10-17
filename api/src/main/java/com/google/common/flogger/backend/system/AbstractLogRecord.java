/*
 * Copyright (C) 2015 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.logging.Level.WARNING;

import com.google.common.flogger.LogSite;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.MessageUtils;
import com.google.common.flogger.backend.Metadata;
import java.util.Arrays;
import java.util.logging.LogRecord;

/**
 * Abstract base class for java.util compatible log records produced by system backends.
 *
 * <p>Note: Even though there is currently only one sub-class available, this class exists to allow
 * logs handling code to cast a {@link LogRecord} to a more stable API, allowing new sub-classes
 * to be added easily if needed.
 */
public abstract class AbstractLogRecord extends LogRecord {
  private final LogData data;
  private final Metadata scope;

  /**
   * Constructs a log record for normal logging without filling in format specific fields.
   * Subclasses calling this constructor are expected to additionally call {@link #setMessage} and
   * {@link #setThrown}.
   */
  AbstractLogRecord(LogData data, Metadata scope) {
    super(data.getLevel(), null);
    this.data = data;
    this.scope = checkNotNull(scope, "scope");

    // Apply any data which is known or easily available without any effort.
    LogSite logSite = data.getLogSite();
    setSourceClassName(logSite.getClassName());
    setSourceMethodName(logSite.getMethodName());
    setLoggerName(data.getLoggerName());
    setMillis(NANOSECONDS.toMillis(data.getTimestampNanos()));
  }

  /**
   * Constructs a log record in response to an exception during a previous logging attempt. A
   * synthetic error message is generated from the original log data and the given exception is set
   * as the cause. The level of this record is the maximum of WARNING or the original level.
   */
  AbstractLogRecord(RuntimeException error, LogData data, Metadata scope) {
    this(data, scope);
    // Re-target this log message as a warning (or above) since it indicates a real bug.
    setLevel(data.getLevel().intValue() < WARNING.intValue() ? WARNING : data.getLevel());
    setThrown(error);
    StringBuilder errorMsg =
        new StringBuilder("LOGGING ERROR: ").append(error.getMessage()).append('\n');
    safeAppend(data, errorMsg);
    setMessage(errorMsg.toString());
  }

  /**
   * Returns the {@link LogData} instance encapsulating the current fluent log statement.
   *
   * <p>The LogData instance is effectively owned by this log record but must still be considered
   * immutable by anyone using it (as it may be processed by multiple log handlers).
   */
  public final LogData getLogData() {
    return data;
  }

  /**
   * Returns the immutable {@link Metadata} scope which should be applied to the current log
   * statement. Scope metadata should be merged with log-site metadata to form a unified view. This
   * is best handled via the {@code MetadataProcessor} API.
   */
  public final Metadata getScope() {
    return scope;
  }

  @Override
  public String toString() {
    // Note that this toString() method is _not_ safe against exceptions thrown by user toString().
    StringBuilder out = new StringBuilder();
    out.append(getClass().getSimpleName())
        .append(" {\n  message: ")
        .append(getMessage())
        .append("\n  arguments: ")
        .append(getParameters() != null ? Arrays.asList(getParameters()) : "<none>")
        .append('\n');
    safeAppend(getLogData(), out);
    out.append("\n}");
    return out.toString();
  }

  private static void safeAppend(LogData data, StringBuilder out) {
    out.append("  original message: ");
    if (data.getTemplateContext() == null) {
      out.append(data.getLiteralArgument());
    } else {
      // We know that there's at least one argument to display here.
      out.append(data.getTemplateContext().getMessage());
      out.append("\n  original arguments:");
      for (Object arg : data.getArguments()) {
        out.append("\n    ").append(MessageUtils.safeToString(arg));
      }
    }
    Metadata metadata = data.getMetadata();
    if (metadata.size() > 0) {
      out.append("\n  metadata:");
      for (int n = 0; n < metadata.size(); n++) {
        out.append("\n    ");
        out.append(metadata.getKey(n).getLabel()).append(": ").append(metadata.getValue(n));
      }
    }
    out.append("\n  level: ").append(data.getLevel());
    out.append("\n  timestamp (nanos): ").append(data.getTimestampNanos());
    out.append("\n  class: ").append(data.getLogSite().getClassName());
    out.append("\n  method: ").append(data.getLogSite().getMethodName());
    out.append("\n  line number: ").append(data.getLogSite().getLineNumber());
  }
}
