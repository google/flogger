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
import com.google.common.flogger.LogContext;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.MetadataHandler;
import com.google.common.flogger.backend.MetadataProcessor;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.context.Tags;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;

import java.util.concurrent.TimeUnit;

import static com.google.common.flogger.backend.log4j2.Log4j2LogMessageFormatter.SimpleLogHandler;

/**
 * Class that represents a log entry that can be written to log4j2.
 */
final class Log4j2SimpleLogEvent implements SimpleLogHandler {
  private static final MetadataHandler<MetadataKey.KeyValueHandler> HANDLER = MetadataHandler
      .builder((MetadataHandler.ValueHandler<Object, MetadataKey.KeyValueHandler>) (key, value, kvh) -> {
          if (key.getClass().equals(LogContext.Key.TAGS.getClass())) {
            processTags(key, value, kvh);
          } else {
            // In theory a user can define a custom tag and use it as a MetadataKey. Those keys shall be treated
            // in the same way as LogContext.Key.TAGS when used as a MetadataKey.
            // Might be removed if visibility of MetadataKey#clazz changes.
            if (value instanceof Tags) {
              processTags(key, value, kvh);
            } else {
              ValueQueue.appendValues(key.getLabel(), value, kvh);
            }
          }
      })
      .setDefaultRepeatedHandler((key, values, kvh) -> values.forEachRemaining(v -> kvh.handle(key.getLabel(), v)))
      .build();

  // Note: Currently the logger is only used to set the logger name in the log
  // event and that looks
  // like it might always be identical to the fluent logger name, so this field
  // might be redundant.
  private final Logger logger;
  private final LogData logData;
  // The following fields are set when handleFormattedLogMessage() is called.
  // Level and message will be set to valid values, but the cause is nullable.
  //
  // Note: The log4j level is only used once elsewhere, so it could easily removed
  // to reduce the
  // size of allocations and just recalculated from LogData.
  private Level level = null;
  private String message = null;
  private Throwable thrown = null;

  private Log4j2SimpleLogEvent(Logger logger, LogData logData) {
    this.logger = logger;
    this.logData = logData;
    Log4j2LogDataFormatter.format(logData, this);
  }

  private Log4j2SimpleLogEvent(Logger logger, LogData badLogData, RuntimeException error) {
    this.logger = logger;
    this.logData = badLogData;
    Log4j2LogDataFormatter.formatBadLogData(error, badLogData, this);
  }

  private static void processTags(MetadataKey<Object> key, Object value, MetadataKey.KeyValueHandler kvh) {
    ValueQueue valueQueue = ValueQueue.appendValueToNewQueue(value);
    // Unlike single metadata (which is usually formatted as a single value), tags are always formatted as a list.
    // Given the tags: tags -> foo=[bar], it will be formnatted to tags=[foo=bar].
    ValueQueue.appendValues(key.getLabel(), valueQueue.size() == 1 ? ImmutableList.copyOf(valueQueue) : valueQueue, kvh);
  }

  /**
   * Creates a {@link Log4j2SimpleLogEvent} for a normal log statement from the
   * given data.
   */
  static Log4j2SimpleLogEvent create(Logger logger, LogData data) {
    return new Log4j2SimpleLogEvent(logger, data);
  }

  /**
   * Creates a {@link Log4j2SimpleLogEvent} in the case of an error during
   * logging.
   */
  static Log4j2SimpleLogEvent error(Logger logger, RuntimeException error, LogData data) {
    return new Log4j2SimpleLogEvent(logger, data, error);
  }

  @Override
  public void handleFormattedLogMessage(java.util.logging.Level level, String message, Throwable thrown) {
    this.level = Log4j2LoggerBackend.toLog4jLevel(level);
    this.message = message;
    this.thrown = thrown;
  }

  Level getLevel() {
    return level;
  }

  LogEvent asLoggingEvent() {
    // We do not support 'MDC.getContext()' and 'NDC.getStack()' and we do not make any attempt to merge Log4j2
    // context data with Flogger's context data. Instead, users should use the ScopedLoggingContext (Grpc).
    //
    // Flogger's ScopedLoggingContext allows to include additional metadata and tags into logs which are
    // written from current thread.
    //
    // Example:
    // try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
    //          .getContextApiSingleton()
    //          .newContext()
    //          .withMetadata(COUNT_KEY, 23)
    //          .withTags(Tags.builder().addTag("foo").addTag("baz", "bar").addTag("baz", "bar2").build())
    //          .install()) {
    //
    //      // do business logic that triggers logs
    // }
    //
    // The statement above will result in adding the following data to the context map:
    // {count=23, tags=[baz=bar, baz=bar2, foo]}.
    // By using '%X{key}' or '%X' in the ConversionPattern of an appender the metadata can be included in the
    // logs.

    // The fully qualified class name of the logger instance is normally used to compute the log
    // location (file, class, method, line number) from the stacktrace. Since we already have the
    // log location in hand we don't need this computation. By passing in null as fully qualified
    // class name of the logger instance we ensure that the log location computation is disabled.
    // this is important since the log location computation is very expensive.
    return Log4jLogEvent.newBuilder()
            .setLoggerName(logger.getName())
            .setLoggerFqcn(null)
            .setLevel(level)
            .setMessage(new SimpleMessage(message))
            .setThreadName(Thread.currentThread().getName())
            // Don't use Duration here as (a) it allocates and (b) we can't allow error on overflow.
            .setTimeMillis(TimeUnit.NANOSECONDS.toMillis(logData.getTimestampNanos()))
            .setThrown(thrown != null ? Throwables.getRootCause(thrown) : null)
            .setIncludeLocation(true)
            .setSource(getLocationInfo())
            .setContextData(createContextMap())
            .build();
  }

  private StringMap createContextMap() {
    MetadataProcessor metadataProcessor = MetadataProcessor.forScopeAndLogSite(
            Platform.getInjectedMetadata(), logData.getMetadata());

    StringMap contextData = ContextDataFactory.createContextData(metadataProcessor.keyCount());
    metadataProcessor.process(HANDLER, (key, value) ->
            contextData.putValue(key, ValueQueue.maybeWrap(value, contextData.getValue(key))));

    contextData.freeze();

    return contextData;
  }

  private StackTraceElement getLocationInfo() {
    LogSite logSite = logData.getLogSite();
    return new StackTraceElement(logSite.getClassName(), logSite.getMethodName(), logSite.getFileName(),
        logSite.getLineNumber());
  }

  @Override
  public String toString() {
    // Note that this toString() method is _not_ safe against exceptions thrown by
    // user toString().
    StringBuilder out = new StringBuilder();
    out.append(getClass().getSimpleName()).append(" {\n  message: ").append(message).append('\n');
    Log4j2LogDataFormatter.appendLogData(logData, out);
    out.append("\n}");
    return out.toString();
  }
}