/*
 * Copyright (C) 2018 The Flogger Authors.
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

import com.google.common.flogger.MetadataKey.KeyValueHandler;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Formats key/value pairs as a human readable string on the end of log statements. The format is:
 *
 * <pre>
 *   Log Message PREFIX[ key1=value1 key2=value2 ]
 * </pre>
 *
 * or
 *
 * <pre>
 *   Multi line
 *   Log Message
 *   PREFIX[ key1=value1 key2=value2 ]
 * </pre>
 *
 * Note that:
 *
 * <ul>
 *   <li>Key/value pairs are appended in the order they are handled.
 *   <li>If no key/value pairs are handled, the log message is unchanged (no prefix is added).
 *   <li>Keys can be repeated.
 *   <li>Key labels do not need quoting.
 *   <li>String-like values are properly quoted and escaped (e.g. \", \\, \n, \t)
 *   <li>Unsafe control characters in string-like values are replaced by U+FFFD (�).
 *   <li>All key/value pairs are on the "same line" of the log message.
 * </ul>
 *
 * The result is that this string should be fully reparsable (with the exception of replaced unsafe
 * characters) and easily searchable by text based tools such as "grep".
 */
public final class KeyValueFormatter implements KeyValueHandler {
  // If a single-line log message is > NEWLINE_LIMIT characters long, emit a newline first. Having
  // a limit prevents scanning very large messages just to discover they do not contain newlines.
  private static final int NEWLINE_LIMIT = 1000;

  // All fundamental types other than "Character", since that can require escaping.
  private static final Set<Class<?>> FUNDAMENTAL_TYPES =
      new HashSet<Class<?>>(
          Arrays.asList(
              Boolean.class,
              Byte.class,
              Short.class,
              Integer.class,
              Long.class,
              Float.class,
              Double.class));

  /**
   * Helper method to emit metadata key/value pairs in a format consistent with JSON. String
   * values which need to be quoted are JSON escaped, while other values are appended without
   * quoting or escaping. Labels are expected to be JSON "safe", and are never quoted. This format
   * is compatible with various "lightweight" JSON representations.
   */
  public static void appendJsonFormattedKeyAndValue(String label, Object value, StringBuilder out) {
    out.append(label).append('=');
    // We could also consider enums as safe if we used name() rather than toString().
    if (value == null) {
      // Alternately just emit the label without '=' to indicate presence without a value.
      out.append(true);
    } else if (FUNDAMENTAL_TYPES.contains(value.getClass())) {
      out.append(value);
    } else {
      out.append('"');
      appendEscaped(out, value.toString());
      out.append('"');
    }
  }

  // The prefix to add before the key/value pairs (e.g. [<prefix>[ foo=bar ])
  private final String prefix;
  private final String suffix;
  // The buffer originally containing the log message, to which we append context.
  private final StringBuilder out;
  // True once we've handled at least one key/value pair.
  private boolean haveSeenValues = false;

  /**
   * Creates a formatter using the given prefix to append key/value pairs to the current log
   * message.
   */
  public KeyValueFormatter(String prefix, String suffix, StringBuilder out) {
    // Non-public class used frequently so skip null checks (callers are responsible).
    this.prefix = prefix;
    this.suffix = suffix;
    this.out = out;
  }

  @Override
  public void handle(String label, @NullableDecl Object value) {
    if (haveSeenValues) {
      out.append(' ');
    } else {
      // At this point 'out' contains only the log message we are appending to.
      if (out.length() > 0) {
        out.append(out.length() > NEWLINE_LIMIT || out.indexOf("\n") != -1 ? '\n' : ' ');
      }
      out.append(prefix);
      haveSeenValues = true;
    }
    appendJsonFormattedKeyAndValue(label, value, out);
  }

  /** Terminates handling of key/value pairs, leaving the originally supplied buffer modified. */
  public void done() {
    if (haveSeenValues) {
      out.append(suffix);
    }
  }

  private static void appendEscaped(StringBuilder out, String s) {
    int start = 0;
    // Most of the time this loop is executed zero times as there are no escapable chars.
    for (int idx = nextEscapableChar(s, start); idx != -1; idx = nextEscapableChar(s, start)) {
      out.append(s, start, idx);
      start = idx + 1;
      char c = s.charAt(idx);
      switch (c) {
        case '"':
        case '\\':
          break;

        case '\n':
          c = 'n';
          break;

        case '\r':
          c = 'r';
          break;

        case '\t':
          c = 't';
          break;

        default:
          // All that remains are unprintable ASCII control characters. It seems reasonable to
          // replace them since the calling code is in complete control of these values and they are
          // meant to be human readable. Use the Unicode replacement character '�'.
          out.append('\uFFFD');
          continue;
      }
      out.append("\\").append(c);
    }
    out.append(s, start, s.length());
  }

  private static int nextEscapableChar(String s, int n) {
    for (; n < s.length(); n++) {
      char c = s.charAt(n);
      if (c < 0x20 || c == '"' || c == '\\') {
        return n;
      }
    }
    return -1;
  }
}
