/*
 * Copyright (C) 2017 The Flogger Authors.
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

package com.google.common.flogger.backend;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.MetadataKey.KeyValueHandler;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Helper class for formatting LogData as text. This class is useful for any logging backend which
 * performs unstructured, text only, logging. Note however that it makes several assumptions
 * regarding metadata and formatting, which may not apply to every text based logging backend.
 *
 * <p>This primarily exists to support both the JDK logging classes and text only Android backends.
 * Code in here may be factored out as necessary to support other use cases in future.
 *
 * <p>If a text based logger backend is not performance sensitive, then it should just append the
 * log message and metadata to a local buffer using {@code appendFormatted(...)}. For example:
 *
 * <pre>{@code
 * StringBuilder buffer = new StringBuilder();
 * // Optional prefix goes here...
 * SimpleMessageFormatter.appendFormatted(logData, scopeMetadata, METADATA_HANDLER, buffer);
 * // Optional suffix goes here...
 * String message = buffer.toString();
 * }</pre>
 *
 * <p>If performance is an issue and no additional formatting is being done by the backend, then
 * {@link #mustBeFormatted(LogData, Metadata, Set)} can be used to help determine situations in
 * which it is safe to just use the literal log message without creating an additional buffer.
 */
public final class SimpleMessageFormatter {
  @SuppressWarnings("ConstantCaseForConstants")
  private static final Set<MetadataKey<?>> DEFAULT_KEYS_TO_IGNORE =
      Collections.<MetadataKey<?>>singleton(LogContext.Key.LOG_CAUSE);

  private static final MetadataHandler<KeyValueHandler> DEFAULT_HANDLER =
      MetadataKeyValueHandlers.getDefaultHandler(DEFAULT_KEYS_TO_IGNORE);

  /**
   * Appends the formatted message and metadata to the given buffer using the default metadata
   * handler. See {@link #appendFormatted(LogData, Metadata, MetadataHandler, StringBuilder)} for
   * more information.
   *
   * @param logData the log statement data.
   * @param scope additional scoped metadata (use {@code Metadata.empty()} if the logging backend
   *     does not support scoped metadata).
   * @param buffer destination buffer in to which the log message and metadata will be appended.
   * @return the given destination buffer (for method chaining).
   */
  public static StringBuilder appendFormatted(
      LogData logData, Metadata scope, StringBuilder buffer) {
    return appendFormatted(logData, scope, DEFAULT_HANDLER, buffer);
  }

  /**
   * Appends the formatted message and metadata to the given buffer using the supplied metadata
   * handler. A custom metadata handler is useful if the logger backend wishes to:
   *
   * <ul>
   *   <li>Ignore more than just the default set of metadata keys (currently just the "cause").
   *   <li>Intercept and capture metadata values for additional processing or logging control.
   * </ul>
   *
   * @param logData the log statement data.
   * @param scope additional scoped metadata (use {@code Metadata.empty()} if the logging backend
   *     does not support scoped metadata).
   * @param metadataHandler a metadata handler for intercepting and dispatching metadata during
   *     formatting.
   * @param buffer destination buffer in to which the log message and metadata will be appended.
   * @return the given destination buffer (for method chaining).
   */
  public static StringBuilder appendFormatted(
      LogData logData,
      Metadata scope,
      MetadataHandler<KeyValueHandler> metadataHandler,
      StringBuilder buffer) {
    BaseMessageFormatter.appendFormattedMessage(logData, buffer);
    KeyValueFormatter kvf = new KeyValueFormatter("[CONTEXT ", " ]", buffer);
    MetadataProcessor.forScopeAndLogSite(scope, logData.getMetadata())
        .process(metadataHandler, kvf);
    kvf.done();
    return buffer;
  }

  /**
   * Returns the single literal value as a string. This method must never be called if the log data
   * has arguments to be formatted.
   *
   * <p>This method is designed to be paired with {@link #mustBeFormatted(LogData, Metadata, Set)}
   * and can always be safely called if that method returned {@code false} for the same log data.
   *
   * @param logData the log statement data.
   * @return the single logged value as a string.
   * @throws IllegalStateException if the log data had arguments to be formatted (i.e. there was a
   *     template context).
   */
  public static String getLiteralLogMessage(LogData logData) {
    return MessageUtils.safeToString(logData.getLiteralArgument());
  }

  /**
   * An internal helper method for logger backends which are aggressively optimized for performance.
   * This method is a best-effort optimization and should not be necessary for most implementations.
   * It is not a stable API and may be removed at some point in the future.
   *
   * <p>This method attempts to determine, for the given log data and scope metadata, if the default
   * message formatting performed by the other methods in this class would just result in the
   * literal log message being used, with no additional formatting.
   *
   * <p>If this method returns {@code false} then the literal log message can be obtained via {@link
   * #getLiteralLogMessage(LogData)}, otherwise {@code appendFormatted()} should be used instead.
   *
   * <p>By calling this class it is possible to more easily detect cases where using buffers to
   * format the log message is not required. Obviously a logger backend my have its own reasons for
   * needing buffering (e.g. prepending log site data) and those must also be taken into account.
   *
   * @param logData the log statement data.
   * @param scope additional scoped metadata (use {@code Metadata.empty()} if the logging backend
   *     does not support scoped metadata).
   * @param singleKeysToIgnore a set of non-repeating log-site only metadata keys which are known
   *     not to appear in the final formatted message.
   */
  public static boolean mustBeFormatted(
      LogData logData, Metadata scope, Set<MetadataKey<?>> singleKeysToIgnore) {
    if (logData.getTemplateContext() != null || scope.size() > 0) {
      return true;
    }
    Metadata metadata = logData.getMetadata();
    int metadataSize = metadata.size();
    // Checking the metadata size only works if we know the keys we are looking for are
    // non-repeating. If we ever have to ignore repeated keys, we'd have to potentially scan much
    // larger metadata.
    boolean containsAll = metadataSize <= singleKeysToIgnore.size();
    for (int n = 0; containsAll && n < metadataSize; n++) {
      MetadataKey<?> key = metadata.getKey(n);
      containsAll = !key.canRepeat() && singleKeysToIgnore.contains(key);
    }
    return !containsAll;
  }

  /**
   * Callback made when formatting of a log message is complete. This is preferable to having the
   * formatter itself hold state because in some common situations an instance of the formatter need
   * not even be allocated.
   */
  public interface SimpleLogHandler {
    /**
     * Handles a single formatted log statement with the given level, message and "cause". This is
     * called back exactly once, from the same thread, for every call made to {@link #format}.
     */
    void handleFormattedLogMessage(Level level, String message, @NullableDecl Throwable thrown);
  }

  /**
   * Applies the default formatting to the log message and any metadata, calling the supplied
   * receiver with the results.
   *
   * <p>Important: This method is designed to only be used by logger backends for which:
   *
   * <ul>
   *   <li>Default formatting is desired and no additional formatting is applied to the result.
   *   <li>Only the "cause" metadata (keyed off {@link LogContext.Key#LOG_CAUSE}) is handled
   *       separately.
   * </ul>
   *
   * <p>If any of these points do not apply, then you should use {@link #appendFormatted(LogData,
   * Metadata, MetadataHandler, StringBuilder) appendFormatted()} instead.
   *
   * <p>Note that this method may be deprecated and removed at some point along with the {@link
   * SimpleLogHandler} interface, and backends are encouraged to migrate away from it in favour of
   * {@link #appendFormatted(LogData, Metadata, StringBuilder)}.
   *
   * @param logData the log statement data.
   * @param scope additional scoped metadata (use {@code Metadata.empty()} if the logging backend
   *     does not support scoped metadata).
   * @param receiver callback handler for the completed log message and additional data.
   */
  public static void format(LogData logData, Metadata scope, SimpleLogHandler receiver) {
    String message;
    if (mustBeFormatted(logData, scope, DEFAULT_KEYS_TO_IGNORE)) {
      message = appendFormatted(logData, scope, DEFAULT_HANDLER, new StringBuilder()).toString();
    } else {
      message = getLiteralLogMessage(logData);
    }
    Throwable cause = logData.getMetadata().findValue(LogContext.Key.LOG_CAUSE);
    receiver.handleFormattedLogMessage(logData.getLevel(), message, cause);
  }

  // ---- Everything below this point is deprecated and will be removed. ----

  /**
   * @deprecated You must obtain and pass in scoped metadata (or explicitly pass an empty scope).
   */
  @Deprecated
  public static void format(LogData logData, SimpleLogHandler receiver) {
    format(logData, Metadata.empty(), receiver);
  }

  /**
   * Note: If you were previously calling this with {@link Option#WITH_LOG_SITE} then you should
   * replace that call with the following code snippet:
   *
   * <pre>{@code
   * // get scope metadata or use Metadata.empty() to ignore scopes altogether
   * Metadata scope = ...;
   * StringBuilder buffer = new StringBuilder();
   * if (MessageUtils.appendLogSite(logData.getLogSite(), buffer)) {
   *   buffer.append(" ");
   * }
   * String message = appendFormatted(logData, scope, buffer).toString();
   * Level logLevel = logData.getLevel();
   * Throwable cause = logData.getMetadata().findValue(LogContext.Key.LOG_CAUSE);
   * // Use log level, message and cause as before, but without needing a receiver ...
   * }</pre>
   *
   * @deprecated Prepending the log site is no longer directly supported by this class.
   */
  @Deprecated
  static void format(LogData logData, SimpleLogHandler receiver, Option option) {
    String message;
    switch (option) {
      case WITH_LOG_SITE:
        StringBuilder buffer = new StringBuilder();
        if (MessageUtils.appendLogSite(logData.getLogSite(), buffer)) {
          buffer.append(" ");
        }
        message = appendFormatted(logData, Metadata.empty(), buffer).toString();
        Throwable cause = logData.getMetadata().findValue(LogContext.Key.LOG_CAUSE);
        receiver.handleFormattedLogMessage(logData.getLevel(), message, cause);
        break;

      case DEFAULT:
        format(logData, Metadata.empty(), receiver);
        break;
    }
  }

  /**
   * @deprecated Use BaseMessageFormatter and MetadataProcessor/MetadataHandler/KeyValueFormatter to
   *     implement formatting according to your needs.
   */
  @Deprecated
  enum Option {
    // Default option.
    DEFAULT,
    // Prepend log site information in the form of [class].[methodName]:[lineNumber] [Message]
    WITH_LOG_SITE
  }

  private SimpleMessageFormatter() {}
}
