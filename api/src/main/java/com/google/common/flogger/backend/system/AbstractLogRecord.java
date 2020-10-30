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
import com.google.errorprone.annotations.DoNotCall;
import java.util.Arrays;
import java.util.logging.LogRecord;

/**
 * Abstract base class for {@code java.util.logging} compatible log records produced by logger
 * backends. Note that the API in this class is not stable, and should not be relied upon outside
 * the core Flogger libraries.
 *
 * <p>Note that currently, due to the mismatch between Java brace-format and printf (Flogger's
 * default placeholder syntax) these log records are always expected to have a formatted message
 * string and no parameters. If you need to process the message in a structured way, it's best to
 * re-parse the format string from the underlying {@link LogData} via the {@code TemplateContext}.
 *
 * <p>Currently any attempt to reset either the message or parameters from outside this class or its
 * subclasses will silently fail, but in future it may start throwing {@code
 * UnsupportedOperationException}.
 */
// TODO(dbeaumont): Make implementation more immutable by overriding setter methods.
public abstract class AbstractLogRecord extends LogRecord {
  private static final Object[] NO_PARAMETERS = new Object[0];

  private final LogData data;
  private final Metadata scope;

  /**
   * Constructs a log record for normal logging without filling in format specific fields.
   * Subclasses calling this constructor are expected to additionally call {@link #setThrown} and
   * {@link #setMessageImpl} (note that calling {@link #setMessage} has no effect).
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

    // Some code resets parameters when it discovers "null", so preempt that here by setting the
    // empty array (but remember to do it via the parent class method which isn't a no-op).
    super.setParameters(NO_PARAMETERS);
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
    setMessageImpl(errorMsg.toString());
  }

  /** @deprecated {@code AbstractLogRecord} does not support having parameters set. */
  @Deprecated
  @DoNotCall
  @Override
  public final void setParameters(Object[] parameters) {
    // Eventually this should throw an UnsupportedOperationException for non null/empty parameters.
  }

  /**
   * @deprecated {@code AbstractLogRecord} does not support resetting its message (use {@link
   *     #setMessageImpl(String)} from subclasses).
   */
  @Deprecated
  @DoNotCall
  @Override
  public final void setMessage(String message) {
    // Eventually this should throw an UnsupportedOperationException.
  }

  /**
   * Protected method for subclasses to set the formatted log message (setting the log message from
   * outside the class hierarchy is not permitted).
   */
  protected final void setMessageImpl(String message) {
    super.setMessage(message);
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
      out.append(MessageUtils.safeToString(data.getLiteralArgument()));
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
        out.append("\n    ")
            .append(metadata.getKey(n).getLabel())
            .append(": ")
            .append(MessageUtils.safeToString(metadata.getValue(n)));
      }
    }
    out.append("\n  level: ").append(MessageUtils.safeToString(data.getLevel()));
    out.append("\n  timestamp (nanos): ").append(data.getTimestampNanos());
    out.append("\n  class: ").append(data.getLogSite().getClassName());
    out.append("\n  method: ").append(data.getLogSite().getMethodName());
    out.append("\n  line number: ").append(data.getLogSite().getLineNumber());
  }
}
