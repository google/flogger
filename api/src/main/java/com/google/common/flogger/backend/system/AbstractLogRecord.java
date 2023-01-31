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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.logging.Level.WARNING;

import com.google.common.flogger.LogSite;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LogMessageFormatter;
import com.google.common.flogger.backend.MessageUtils;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.common.flogger.backend.SimpleMessageFormatter;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Abstract base class for {@code java.util.logging} compatible log records produced by Flogger
 * backends. This class behaves externally like {@link LogRecord} but supports more memory efficient
 * message formatting via the {@link #appendFormattedMessageTo(StringBuilder)} method.
 *
 * <p>This class supports three distinct modes of operation, depending on the state of the message
 * and/or parameters:
 *
 * <h2>Null message, null or empty parameters</h2>
 *
 * This is the initial state of the log record, and indicates that the log message has not been
 * formatted. This is a non-visible, internal state, since any attempt to read the message string
 * will result it it being formatted and subsequently cached.
 *
 * <p>In this state the formatted message <em>can</em> still be appended to a buffer via {@link
 * #appendFormattedMessageTo(StringBuilder)} without retaining a cached string copy. If the log
 * record is expected to be formatted only once during logging, this will save an extraneous string
 * copy from being made, which in some systems could save a lot of allocations and memory copying.
 *
 * <h2>Non-null message, null or empty parameters</h2>
 *
 * This state is reached either when {@link #getMessage()} is first called, or if an explicit
 * non-null message is set via {@link #setMessage(String)} (without setting any parameters). In this
 * state, the message is considered to be formatted, and just returned via {@code getMessage()}.
 *
 * <h2>Non-null message, non-empty parameters</h2>
 *
 * This state is only reached if a user calls both {@link #setMessage(String)} and {@link
 * #setParameters(Object[])}. In this state the message is treated as is it were a brace-format log
 * message, and no formatting is attempted. Any relationship between this value and the log message
 * implied by the contained {@link LogData} and {@link Metadata} is lost.
 *
 * <p>For many reasons it is never a good idea for users to modify unknown {@link LogRecord}
 * instances, but this does happen occasionally, so this class supports that in a best effort way,
 * but users are always recommended to copy {@link LogRecord} instances if they need to modify them.
 *
 * <h2>Corollary</h2>
 *
 * <p>Because of the defined states above there are a few small, but necessary, changes to behaviour
 * in this class as compared to the "vanilla" JDK {@link LogRecord}.
 *
 * <ul>
 *   <li>Since the "message" field being {@code null} indicates a private state, calling {@code
 *       setMessage(null)} from outside this class is equivalent to calling {@code setMessage("")},
 *       and will not reset the instance to its initial "unformatted" state. This is within
 *       specification for {@code LogRecord} since the documentation for {@link #getMessage()} says
 *       that a return value of {@code null} is equivalent to the empty string.
 *   <li>Setting the parameters to {@code null} from outside this class will reset the parameters to
 *       a static singleton empty array. From outside this class, {@link #getParameters} is never
 *       observed to contain {@code null}. This is also within specification for {@code LogRecord}.
 *   <li>Setting parameters from outside this class (to any value) will also result in the log
 *       message being formatted and cached (if it hadn't been set already). This is to avoid
 *       situations in which parameters are set, but the underlying message is still {@code null}.
 *   <li>{@code ResourceBundles} are not supported by {@code AbstractLogRecord} and any attempt to
 *       set them is ignored.
 * </ul>
 */
public abstract class AbstractLogRecord extends LogRecord {
  // Note that Formatter instances are mutable and protect state via synchronization. We never
  // modify the instance however, and only call the synchronized formatMessage() helper method, so
  // we can use it safely without additional locking.
  private static final Formatter jdkMessageFormatter =
      new Formatter() {
        // Never invoked by our code.
        @Override
        public String format(LogRecord record) {
          throw new UnsupportedOperationException();
        }
      };

  private static final Object[] NO_PARAMETERS = new Object[0];

  private final LogData data;
  private final MetadataProcessor metadata;

  /**
   * Constructs a log record for normal logging without filling in format specific fields.
   * Subclasses calling this constructor are expected to additionally call {@link #setThrown} and
   * perhaps {@link #setMessage} (depending on whether eager message caching is desired).
   */
  protected AbstractLogRecord(LogData data, Metadata scope) {
    super(data.getLevel(), null);
    this.data = data;
    this.metadata = MetadataProcessor.forScopeAndLogSite(scope, data.getMetadata());

    // Apply any data which is known or easily available without any effort.
    LogSite logSite = data.getLogSite();
    setSourceClassName(logSite.getClassName());
    setSourceMethodName(logSite.getMethodName());
    setLoggerName(data.getLoggerName());
    setMillis(NANOSECONDS.toMillis(data.getTimestampNanos()));

    // It was discovered that some null-hostile application code resets "parameters" to an empty
    // array when it discovers null, so preempt that here by initializing the parameters array (but
    // do it via the parent class method which doesn't have side effects). This should reduce the
    // risk of needless message caching caused by calling setParameters() from application code.
    super.setParameters(NO_PARAMETERS);
  }

  /**
   * Constructs a log record in response to an exception during a previous logging attempt. A
   * synthetic error message is generated from the original log data and the given exception is set
   * as the cause. The level of this record is the maximum of WARNING or the original level.
   */
  protected AbstractLogRecord(RuntimeException error, LogData data, Metadata scope) {
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
   * Returns the formatter used when formatting {@link LogData}. This is not used if the log message
   * was set explicitly, and can be overridden to supply a different formatter without necessarily
   * requiring a new field in this class (to cut down on instance size).
   */
  protected LogMessageFormatter getLogMessageFormatter() {
    return SimpleMessageFormatter.getDefaultFormatter();
  }

  @Override
  public final void setParameters(Object[] parameters) {
    // IMPORTANT: We call getMessage() to cache the internal formatted message if someone indicates
    // they want to change the parameters. This is to avoid a situation in which parameters are set,
    // but the underlying message is still null. Do this first to switch internal states.
    String unused = getMessage();
    // Now handle setting parameters as normal.
    if (parameters == null) {
      parameters = NO_PARAMETERS;
    }
    super.setParameters(parameters);
  }

  @Override
  public final void setMessage(String message) {
    if (message == null) {
      message = "";
    }
    super.setMessage(message);
  }

  @Override
  public final String getMessage() {
    String cachedMessage = super.getMessage();
    if (cachedMessage != null) {
      return cachedMessage;
    }
    String formattedMessage = getLogMessageFormatter().format(data, metadata);
    super.setMessage(formattedMessage);
    return formattedMessage;
  }

  /**
   * Appends the formatted log message to the given buffer. In idiomatic usage, this method will
   * avoid making a separate copy of the formatted message, and instead append directly into the
   * given buffer. In cases where are log record is only formatted once, this can save a lot of
   * needless string copying.
   *
   * <p>If {@code #getMessage()} has been called on this log record before calling this method, the
   * cached result will appended instead (though the effect will be the same).
   *
   * <p>If this log record has had a message and parameters explicitly set, it is formatted as if by
   * the default implementation of {@link Formatter#formatMessage(LogRecord)}, and the result is not
   * cached.
   */
  @CanIgnoreReturnValue
  public final StringBuilder appendFormattedMessageTo(StringBuilder buffer) {
    String cachedMessage = super.getMessage();
    if (cachedMessage == null) {
      // This code path is critical for optimized use of AbstractLogRecord. If the log message was
      // not reset at any point up to now, we can append the formatted message directly to an output
      // buffer without first creating a copy in a String instance. This mean that by using
      // AbstractLogRecord idiomatically and having a Formatter which can specialize against this
      // class, you can avoid making a complete copy of the message  during logging. Formatters
      // which don't specialize and call getMessage() instead will get the same result, just with an
      // extra String copy.
      getLogMessageFormatter().append(data, metadata, buffer);
    } else if (getParameters().length == 0) {
      // If getMessage() was called then the cost of making a String copy was already made, so just
      // append it here (or this could be a user supplied string without additional parameters).
      buffer.append(cachedMessage);
    } else {
      // User supplied message and parameters means we just default to standard Java behaviour every
      // time, which is fine because this should almost never happen.
      buffer.append(jdkMessageFormatter.formatMessage(this));
    }
    return buffer;
  }

  /**
   * Returns the formatted log message, caching the result in the common case. This method only
   * differs from {@link #getMessage()} if this log record was modified externally after creation
   * by resetting the log message or parameters.
   *
   * <p>Use {@link #appendFormattedMessageTo(StringBuilder)} whenever a buffer is already available.
   */
  public final String getFormattedMessage() {
    if (getParameters().length == 0) {
      return getMessage();
    }
    return jdkMessageFormatter.formatMessage(this);
  }

  // Unsupported by AbstractLogRecord.
  @Override
  public final void setResourceBundle(ResourceBundle bundle) {}

  // Unsupported by AbstractLogRecord.
  @Override
  public final void setResourceBundleName(String name) {}

  /**
   * Returns a snapshot of this log record, copied and flattened to a plain, mutable {@link
   * LogRecord} instance. The log message of the new log record is the formatted message from this
   * instance, so the new new log record never has any parameters.
   *
   * <p>Since {@code AbstractLogRecord} has synthetic fields, it's not safely mutable itself. Once
   * copied, the plain LogRecord will not benefit from some of the potential optimizations which
   * {@code AbstractLogRecord} can be subject to.
   */
  public final LogRecord toMutableLogRecord() {
    LogRecord copy = new LogRecord(getLevel(), getFormattedMessage());
    copy.setParameters(NO_PARAMETERS);
    copy.setSourceClassName(getSourceClassName());
    copy.setSourceMethodName(getSourceMethodName());
    copy.setLoggerName(getLoggerName());
    copy.setMillis(getMillis());
    copy.setThrown(getThrown());

    // Not set explicitly normally, but a copy should be safe (even if it's null). We don't copy
    // the sequence number since that's intended to be unique per LogRecord, and resource bundles
    // are not supported.
    copy.setThreadID(getThreadID());
    return copy;
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
   * Returns the immutable {@link MetadataProcessor} which provides a unified view of scope and log
   * site metadata. This should be used in preference to {@link Metadata} available from {@link
   * LogData} which represents only the log site.
   */
  public final MetadataProcessor getMetadataProcessor() {
    return metadata;
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
