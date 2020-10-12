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

import static com.google.common.flogger.backend.FormatOptions.FLAG_LEFT_ALIGN;
import static com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_ALT_FORM;
import static com.google.common.flogger.backend.FormatOptions.FLAG_UPPER_CASE;
import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.context.Tags;
import com.google.common.flogger.parameter.DateTimeFormat;
import com.google.common.flogger.parameter.Parameter;
import com.google.common.flogger.parameter.ParameterVisitor;
import com.google.common.flogger.parser.MessageBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Formattable;
import java.util.FormattableFlags;
import java.util.Formatter;
import java.util.Locale;
import java.util.Map;
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
 */
public final class SimpleMessageFormatter extends MessageBuilder<StringBuilder>
    implements ParameterVisitor {
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
   * Format options.
   */
  enum Option {
    // Default option.
    DEFAULT,
    // Prepend log site information in the form of [class].[methodName]:[lineNumber] [Message]
    WITH_LOG_SITE
  }

  /** A predicate to determine which metadata entries should be formatted. */
  public interface MetadataPredicate {
    boolean shouldFormat(MetadataKey<?> key);
  }

  // Literal string to be inlined whenever a placeholder references a non-existent argument.
  private static final String MISSING_ARGUMENT_MESSAGE = "[ERROR: MISSING LOG ARGUMENT]";

  // Literal string to be appended wherever additional unused arguments are provided.
  private static final String EXTRA_ARGUMENT_MESSAGE = " [ERROR: UNUSED LOG ARGUMENTS]";

  // It would be more "proper" to use "Locale.getDefault(Locale.Category.FORMAT)" here, but also
  // removes the capability of optimising certain formatting operations.
  private static final Locale FORMAT_LOCALE = Locale.ROOT;

  // Default metadata keys to add to formatted strings. (No lambdas here for compatibility.)
  // When the Flogger core library supports JDK 8, this can be converted to a lambda or Predicate.
  @SuppressWarnings("UnnecessaryAnonymousClass")
  private static final MetadataPredicate FORMAT_ALL_METADATA =
      new MetadataPredicate() {
        @Override
        public boolean shouldFormat(MetadataKey<?> key) {
          return true;
        }
      };

  /**
   * Formats the log message and any metadata for the given {@link LogData}, calling the supplied
   * receiver object with the results.
   */
  public static void format(LogData logData, SimpleLogHandler receiver) {
    format(logData, receiver, Option.DEFAULT);
  }

  /**
   * Formats the log message and any metadata for the given {@link LogData}, calling the supplied
   * receiver object with the results with a given option.
   */
  static void format(LogData logData, SimpleLogHandler receiver, Option option) {
    format(logData, receiver, option, FORMAT_ALL_METADATA);
  }

  /**
   * Formats the log message and any metadata for the given {@link LogData}, calling the supplied
   * receiver object with the results with a given option and metadata keys filter.
   */
  static void format(
      LogData logData,
      SimpleLogHandler receiver,
      Option option,
      MetadataPredicate metadataPredicate) {
    Metadata metadata = logData.getMetadata();
    Throwable thrown = metadata.findValue(LogContext.Key.LOG_CAUSE);
    // Either no metadata, or only "cause" and ignored metadata means we don't need to do additional
    // formatting. This is pretty common and will save some work and object allocations.
    boolean hasOnlyKnownMetadata = true;
    for (int n = 0; n < metadata.size(); n++) {
      MetadataKey<?> key = metadata.getKey(n);
      if (shouldFormat(key, metadataPredicate)) {
        hasOnlyKnownMetadata = false;
        break;
      }
    }

    TemplateContext ctx = logData.getTemplateContext();
    String message;
    if (ctx == null) {
      message = formatLiteralMessage(logData, option, hasOnlyKnownMetadata, metadataPredicate);
    } else {
      StringBuilder buffer = formatMessage(logData, option);
      if (!hasOnlyKnownMetadata) {
        appendContext(buffer, metadata, metadataPredicate);
      }
      message = buffer.toString();
    }
    receiver.handleFormattedLogMessage(logData.getLevel(), message, thrown);
  }

  /**
   * Returns a string representation of the user supplied value accounting for any possible runtime
   * exceptions.
   *
   * @param value the value to be formatted.
   * @return a best-effort string representation of the given value, even if exceptions were thrown.
   */
  // TODO: Move to utility class so users don't need to depend on the rest of this class.
  public static String safeToString(Object value) {
    try {
      // TODO(b/36844237): Re-evaluate if this is the best place for null handling (null is also
      // handled for arguments via visitNull() and this is only for literal arguments).
      return value != null ? toString(value) : "null";
    } catch (RuntimeException e) {
      return getErrorString(value, e);
    }
  }

  /**
   * Returns a string representation of the user supplied formattable, accounting for any possible
   * runtime exceptions.
   *
   * @param value the value to be formatted.
   * @return a best-effort string representation of the given value, even if exceptions were thrown.
   */
  private static void safeFormatTo(Formattable value, StringBuilder out, FormatOptions options) {
    // Only care about 3 specific flags for Formattable.
    int formatFlags = options.getFlags() & (FLAG_LEFT_ALIGN | FLAG_UPPER_CASE | FLAG_SHOW_ALT_FORM);
    if (formatFlags != 0) {
      // TODO: Maybe re-order the options flags to make this step easier or use a lookup table.
      // Note that reordering flags would require a rethink of how they are parsed.
      formatFlags = ((formatFlags & FLAG_LEFT_ALIGN) != 0 ? FormattableFlags.LEFT_JUSTIFY : 0)
          | ((formatFlags & FLAG_UPPER_CASE) != 0 ? FormattableFlags.UPPERCASE : 0)
          | ((formatFlags & FLAG_SHOW_ALT_FORM) != 0 ? FormattableFlags.ALTERNATE : 0);
    }
    // We may need to undo an arbitrary amount of appending if there is an error.
    int originalLength = out.length();
    Formatter formatter = new Formatter(out, FORMAT_LOCALE);
    try {
      value.formatTo(formatter, formatFlags, options.getWidth(), options.getPrecision());
    } catch (RuntimeException e) {
      out.setLength(originalLength);
      // We only use a StringBuilder to create the Formatter instance.
      try {
        formatter.out().append(getErrorString(value, e));
      } catch (IOException impossible) { }
    }
  }

  private static String getErrorString(Object value, RuntimeException e) {
    String errorMessage;
    try {
      errorMessage = e.toString();
    } catch (RuntimeException wtf) {
      // Ok, now you're just being silly...
      errorMessage = wtf.getClass().getSimpleName();
    }
    return "{"
        + value.getClass().getName()
        + "@"
        + System.identityHashCode(value)
        + ": "
        + errorMessage
        + "}";
  }

  /**
   * Formats a log message which contains placeholders (i.e. when a template context exists). This
   * does not format only metadata, only the message and its arguments. It may also prepend the
   * message with the log site information (depending on the given formatting option).
   */
  private static StringBuilder formatMessage(LogData logData, Option option) {
    SimpleMessageFormatter formatter =
        new SimpleMessageFormatter(logData.getTemplateContext(), logData.getArguments());
    StringBuilder out = formatter.build();
    if (logData.getArguments().length > formatter.getExpectedArgumentCount()) {
      // TODO(dbeaumont): Do better and look at adding formatted values or maybe just a count?
      out.append(EXTRA_ARGUMENT_MESSAGE);
    }
    if (option == Option.WITH_LOG_SITE) {
      prependLogSite(out, logData.getLogSite());
    }
    return out;
  }

  private static void appendContext(
      StringBuilder out, Metadata metadata, MetadataPredicate metadataPredicate) {
    KeyValueFormatter kvf = new KeyValueFormatter("[CONTEXT ", " ]", out);
    Tags tags = null;
    for (int n = 0; n < metadata.size(); n++) {
      MetadataKey<?> key = metadata.getKey(n);
      if (!shouldFormat(key, metadataPredicate)) {
        continue;
      } else if (key.equals(LogContext.Key.TAGS)) {
        tags = LogContext.Key.TAGS.cast(metadata.getValue(n));
        continue;
      }
      castAndEmit(key, metadata.getValue(n), kvf);
    }
    if (tags != null) {
      emitAllTags(tags, kvf);
    }
    kvf.done();
  }

  // Needed to re-capture the key type locally so the cast value is known to have the same type
  // when passed to the emit method (in the loop it's just a MetadataKey<?>).
  private static <T> void castAndEmit(MetadataKey<T> key, Object value, KeyValueFormatter kvf) {
    key.emit(key.cast(value), kvf);
  }

  /** Emits all the key/value pairs of this Tags instance to the given consumer. */
  private static void emitAllTags(Tags tags, KeyValueFormatter out) {
    for (Map.Entry<String, ? extends Set<Object>> e : tags.asMap().entrySet()) {
      // Remember that tags can exist without values.
      String key = e.getKey();
      Set<Object> values = e.getValue();
      if (!values.isEmpty()) {
        for (Object value : values) {
          out.handle(key, value);
        }
      } else {
        out.handle(key, null);
      }
    }
  }

  private static boolean shouldFormat(MetadataKey<?> key, MetadataPredicate metadataPredicate) {
    // The cause is special and is never formatted like other metadata (it's also the most common,
    // so checking for it first is good).
    return !key.equals(LogContext.Key.LOG_CAUSE) && metadataPredicate.shouldFormat(key);
  }

  // Input argument array reference (not copied).
  private final Object[] args;
  // Buffer into which the message is formatted.
  private final StringBuilder out = new StringBuilder();
  // The start of the next literal sub-section of the message that needs processing.
  private int literalStart = 0;

  private SimpleMessageFormatter(TemplateContext context, Object[] args) {
    super(context);
    this.args = checkNotNull(args, "log arguments");
  }

  @Override
  public void addParameterImpl(int termStart, int termEnd, Parameter param) {
    getParser().unescape(out, getMessage(), literalStart, termStart);
    param.accept(this, args);
    literalStart = termEnd;
  }

  @Override
  public StringBuilder buildImpl() {
    getParser().unescape(out, getMessage(), literalStart, getMessage().length());
    return out;
  }

  @Override
  public void visit(Object value, FormatChar format, FormatOptions options) {
    if (format.getType().canFormat(value)) {
      appendFormatted(out, value, format, options);
    } else {
      appendInvalid(out, value, format.getDefaultFormatString());
    }
  }

  @Override
  public void visitDateTime(Object value, DateTimeFormat format, FormatOptions options) {
    if (value instanceof Date || value instanceof Calendar || value instanceof Long) {
      String formatString =
          options
              .appendPrintfOptions(new StringBuilder("%"))
              .append(options.shouldUpperCase() ? 'T' : 't')
              .append(format.getChar())
              .toString();
      out.append(String.format(FORMAT_LOCALE, formatString, value));
    } else {
      appendInvalid(out, value, "%t" + format.getChar());
    }
  }

  @Override
  public void visitPreformatted(Object value, String formatted) {
    // For unstructured logging we just use the preformatted string.
    out.append(formatted);
  }

  @Override
  public void visitMissing() {
    out.append(MISSING_ARGUMENT_MESSAGE);
  }

  @Override
  public void visitNull() {
    out.append("null");
  }

  // TODO: Factor out this logic more to allow subclasses to easily support other types.
  private static void appendFormatted(
      StringBuilder out, Object value, FormatChar format, FormatOptions options) {
    // Fast path switch statement for commonest cases (we could handle upper-case as a post
    // processing step but it's so uncommon it doesn't seem worth it).
    switch (format) {
      case STRING:
        // String formatting is by far and away the most common case.
        if (!(value instanceof Formattable)) {
          if (options.isDefault()) {
            // %s on a non-Formattable instance is the single most common case by far.
            out.append(safeToString(value));
            return;
          }
          break;
        }
        // Rare but easy to deal with efficiently, and a can support wrapped arguments nicely.
        safeFormatTo((Formattable) value, out, options);
        return;

        // Some other types are really easy when they don't have special format options.
      case DECIMAL:
      case BOOLEAN:
        if (options.isDefault()) {
          out.append(value);
          return;
        }
        break;

      case HEX:
        // Check that if the format options are compatible with "easy" hex formatting. This could
        // be expanded to include width, radix and zero padding (relatively common for hex).
        if (options.filter(FLAG_UPPER_CASE, false, false).equals(options)) {
          // Having called canFormat(), we know the value must be a Number.
          appendHex(out, (Number) value, options);
          return;
        }
        break;

      case CHAR:
        if (options.isDefault()) {
          if (value instanceof Character) {
            out.append(value);
            return;
          }
          int codePoint = ((Number) value).intValue();
          if (Character.isBmpCodePoint(codePoint)) {
            out.append((char) codePoint);
            return;
          }
          out.append(Character.toChars(codePoint));
          return;
        }
        break;

      default:
        // Fall through.
    }
    // Default handle for rare cases that need non-trivial formatting.
    String formatString = format.getDefaultFormatString();
    if (!options.isDefault()) {
      char chr = format.getChar();
      if (options.shouldUpperCase()) {
        // Clear 6th bit to convert lower case ASCII to upper case.
        chr &= (char) ~0x20;
      }
      formatString = options.appendPrintfOptions(new StringBuilder("%")).append(chr).toString();
    }
    out.append(String.format(FORMAT_LOCALE, formatString, value));
  }

  // Visible for testing
  static void appendHex(StringBuilder out, Number number, FormatOptions options) {
    // We know there are no unexpected formatting flags (currently only upper casing is supported).
    boolean isUpper = options.shouldUpperCase();
    // We cannot just call Long.toHexString() as that would get negative values wrong.
    long n = number.longValue();
    // Roughly in order of expected usage.
    if (number instanceof Long) {
      appendHex(out, n, isUpper);
    } else if (number instanceof Integer) {
      appendHex(out, n & 0xFFFFFFFFL, isUpper);
    } else if (number instanceof Byte) {
      appendHex(out, n & 0xFFL, isUpper);
    } else if (number instanceof Short) {
      appendHex(out, n & 0xFFFFL, isUpper);
    } else if (number instanceof BigInteger) {
      String hex = ((BigInteger) number).toString(16);
      out.append(isUpper ? hex.toUpperCase(FORMAT_LOCALE) : hex);
    } else {
      // This will be caught and handled by the logger, but it should never happen.
      throw new RuntimeException("unsupported number type: " + number.getClass());
    }
  }

  private static void appendHex(StringBuilder out, long n, boolean isUpper) {
    if (n == 0) {
      out.append("0");
    } else {
      String hexChars = isUpper ? "0123456789ABCDEF" : "0123456789abcdef";
      // Shift with a value in the range 0..60 and count down in steps of 4. You could unroll this
      // into a switch statement and it might be faster, but it's likely not worth it.
      for (int shift = (63 - Long.numberOfLeadingZeros(n)) & ~3; shift >= 0; shift -= 4) {
        out.append(hexChars.charAt((int) ((n >>> shift) & 0xF)));
      }
    }
  }

  private static void appendInvalid(StringBuilder out, Object value, String formatString) {
    out.append("[INVALID: format=")
        .append(formatString)
        .append(", type=")
        .append(value.getClass().getCanonicalName())
        .append(", value=")
        .append(safeToString(value))
        .append("]");
  }

  private static String formatLiteralMessage(
      LogData logData,
      Option option,
      boolean hasOnlyKnownMetadata,
      MetadataPredicate metadataPredicate) {
    // If a literal message (no arguments) is logged and no metadata exists, just use the string.
    // Having no format arguments is fairly common and this avoids allocating StringBuilders and
    // formatter instances in a lot of situations.
    String message = safeToString(logData.getLiteralArgument());
    if (option == Option.DEFAULT && hasOnlyKnownMetadata) {
      return message;
    }

    StringBuilder builder = new StringBuilder(message);
    if (option == Option.WITH_LOG_SITE) {
      prependLogSite(builder, logData.getLogSite());
    }
    if (!hasOnlyKnownMetadata) {
      // If unknown metadata exists we have to append it to the message (this is not that common
      // because a Throwable "cause" is already handled separately).
      appendContext(builder, logData.getMetadata(), metadataPredicate);
    }
    return builder.toString();
  }

  private static void prependLogSite(StringBuilder out, LogSite logSite) {
    if (logSite == LogSite.INVALID) {
      return;
    }

    int originalLength = out.length();
    out.insert(0, logSite.getClassName());
    out.insert(out.length() - originalLength, '.');
    out.insert(out.length() - originalLength, logSite.getMethodName());
    out.insert(out.length() - originalLength, ':');
    out.insert(out.length() - originalLength, logSite.getLineNumber());
    out.insert(out.length() - originalLength, ' ');
  }

  /**
   * Returns a string representation of the user supplied value. This method should try hard to
   * return a human readable representation, possibly going beyond the default {@code toString()}
   * representation for some well defined types.
   *
   * @param value the non-null value to be formatted.
   * @return a readable string representation of the given value.
   */
  // VisibleForTesting
  static String toString(Object value) {
    if (!value.getClass().isArray()) {
      return String.valueOf(value);
    }
    if (value instanceof int[]) {
      return Arrays.toString((int[]) value);
    }
    if (value instanceof long[]) {
      return Arrays.toString((long[]) value);
    }
    if (value instanceof byte[]) {
      return Arrays.toString((byte[]) value);
    }
    if (value instanceof char[]) {
      return Arrays.toString((char[]) value);
    }
    if (value instanceof short[]) {
      return Arrays.toString((short[]) value);
    }
    if (value instanceof float[]) {
      return Arrays.toString((float[]) value);
    }
    if (value instanceof double[]) {
      return Arrays.toString((double[]) value);
    }
    if (value instanceof boolean[]) {
      return Arrays.toString((boolean[]) value);
    }
    // Non fundamental type array.
    return Arrays.toString((Object[]) value);
  }
}
