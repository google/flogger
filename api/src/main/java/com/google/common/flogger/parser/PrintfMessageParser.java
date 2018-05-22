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

package com.google.common.flogger.parser;

/**
 * A specialized {@link MessageParser} for processing log messages in printf style, as used by
 * {@link String#format}. This is an abstract parser which knows how to
 * process and extract placeholder terms at a high level, but does not impose its own semantics
 * for place-holder types.
 * <p>
 * Typically you should not subclass this class, but instead subclass
 * {@link DefaultPrintfMessageParser}, which provides compatibility with {@link String#format}.
 */
public abstract class PrintfMessageParser extends MessageParser {
  // Assume that anything outside this set of chars is suspicious and not safe.
  private static final String ALLOWED_NEWLINE_PATTERN = "\\n|\\r(?:\\n)?";
  /** The system newline separator for replacing %n. */
  private static final String SYSTEM_NEWLINE = getSafeSystemNewline();
  /**
   * Returns the system newline separator avoiding any issues with security exceptions or
   * "suspicious" values. The only allowed return values are "\n" (default), "\r" or "\r\n".
   */
  static String getSafeSystemNewline() {
    try {
      String unsafeNewline = System.getProperty("line.separator");
      if (unsafeNewline.matches(ALLOWED_NEWLINE_PATTERN)) {
        return unsafeNewline;
      }
    } catch (SecurityException e) {
      // Fall through to default value;
    }
    return "\n";
  }

  /**
   * Parses a single printf-like term from a log message into a message template builder.
   * <p>
   * A simple example of an implicit parameter (the argument index is not specified):
   * <pre>
   * message: "Hello %s World"
   * termStart: 6 ───┚╿╿
   * specStart: 7 ────┤│
   * formatStart: 7 ──╯│
   * return: 8 ────────╯
   * </pre>
   * If this case there is no format specification, so {@code specStart == formatStart}.
   * <p>
   * A complex example with an explicit index:
   * <pre>
   * message: "Hello %2$10d World"
   * termStart: 6 ───┚  ╿ ╿╿
   * specStart: 9 ──────╯ ││
   * formatStart: 11 ─────╯│
   * return: 12 ───────────╯
   * </pre>
   * Note that in this example the given index will be 1 (rather than 2) because printf specifies
   * indices using a 1-based scheme, but internally they are 0-based.
   *
   * @param builder the message template builder.
   * @param index the zero-based argument index for the parameter.
   * @param message the complete log message string.
   * @param termStart the index of the initial '%' character that starts the term.
   * @param specStart the index of the first format specification character (after any optional
   *        index specification).
   * @param formatStart the index of the (first) format character in the term.
   * @return the index after the last character of the term.
   */
  abstract int parsePrintfTerm(
      MessageBuilder<?> builder,
      int index,
      String message,
      int termStart,
      int specStart,
      int formatStart)
      throws ParseException;

  @Override
  public final void unescape(StringBuilder out, String message, int start, int end) {
    unescapePrintf(out, message, start, end);
  }

  @Override
  protected final <T> void parseImpl(MessageBuilder<T> builder) throws ParseException {
    String message = builder.getMessage();
    // The last index we used (needed for $< syntax, initially invalid).
    int lastResolvedIndex = -1;
    // The next index to use for an implicit parameter (can become -1 if a parameter consumes all
    // the remaining arguments).
    int implicitIndex = 0;
    // Find the start of each term in sequence.
    for (int pos = nextPrintfTerm(message, 0); pos >= 0; pos = nextPrintfTerm(message, pos)) {
      // Capture the term start and move on (the character here is always '%').
      int termStart = pos++;
      // At this stage we don't know if any numeric value we parse is going to be part of a
      // parameter index ($nnn$x) or the format specification (%nnnx) but we assume the latter.
      int optionsStart = pos;

      // STEP 1: Parse any numeric value at the start of the term.
      char c;
      int index = 0;
      while (true) {
        if (pos < message.length()) {
          c = message.charAt(pos++);
          // Casting to char makes the result unsigned (so we don't need to test "digit < 0" later).
          int digit = (char) (c - '0');
          if (digit < 10) {
            index = (10 * index) + digit;
            if (index < MAX_ARG_COUNT) {
              continue;
            }
            throw ParseException.withBounds("index too large", message, termStart, pos);
          }
          // We found something other than [0-9] so we've finished parsing our value.
          break;
        }
        throw ParseException.withStartPosition("unterminated parameter", message, termStart);
      }

      // STEP 2: Process the value and determine the parameter's real index.
      if (c == '$') {
        // If was an index, but we could have got here without parsing any digits (ie, "%$")
        int indexLen = (pos - 1) - optionsStart;
        if (indexLen == 0) {
          throw ParseException.withBounds("missing index", message, termStart, pos);
        }
        // We also prohibit leading zeros in any index value (printf indices are 1-based, so the
        // first digit should never be zero).
        if (message.charAt(optionsStart) == '0') {
          throw ParseException.withBounds("index has leading zero", message, termStart, pos);
        }
        // Now correct the index to be 0-based.
        index -= 1;
        // Having got the parameter index, reset the specification start to just after the '$'.
        optionsStart = pos;
        // Read the next character from the message (needed for the next part of the parsing).
        if (pos == message.length()) {
          throw ParseException.withStartPosition("unterminated parameter", message, termStart);
        }
        c = message.charAt(pos++);
      } else if (c == '<') {
        // This is the rare 'relative' indexing mode where you just re-use the last parameter index.
        if (lastResolvedIndex == -1) {
          throw ParseException.withBounds("invalid relative parameter", message, termStart, pos);
        }
        index = lastResolvedIndex;
        // Having got the parameter index, reset the specification start to just after the '<'.
        optionsStart = pos;
        // Read the next character from the message (needed for the next part of the parsing).
        if (pos == message.length()) {
          throw ParseException.withStartPosition("unterminated parameter", message, termStart);
        }
        c = message.charAt(pos++);
      } else {
        // The parsed value was not an index, so we use the current implicit index. We do not need
        // to update the format start in this case and the current character is already correct for
        // the next part of the parsing.
        index = implicitIndex++;
      }

      // STEP 3: Find the index of the type character that terminates the format specification.
      // Remember to decrement pos to account for the fact we were one ahead of the current char.
      pos = findFormatChar(message, termStart, pos - 1);

      // STEP 4: Invoke the term parsing method and reset loop state.
      // Add a parameter to the builder and find where the term ends.
      pos = parsePrintfTerm(builder, index, message, termStart, optionsStart, pos);
      // Before going round again, record the index we just used and update the implicit index.
      lastResolvedIndex = index;
    }
  }

  /**
   * Returns the index of the first unescaped '%' character in message starting at pos (or -1 if not
   * found).
   */
  // VisibleForTesting
  static int nextPrintfTerm(String message, int pos) throws ParseException {
    while (pos < message.length()) {
      if (message.charAt(pos++) != '%') {
        continue;
      }
      if (pos < message.length()) {
        char c = message.charAt(pos);
        if (c == '%' || c == 'n') {
          // We encountered '%%' or '%n', so keep going (these will be unescaped later).
          pos += 1;
          continue;
        }
        // We were pointing at the character after the '%', so adjust back by one.
        return pos - 1;
      }
      // We ran off the end while looking for the character after the first '%'.
      throw ParseException.withStartPosition("trailing unquoted '%' character", message, pos - 1);
    }
    // We never found another unescaped '%'.
    return -1;
  }

  private static int findFormatChar(String message, int termStart, int pos) throws ParseException {
    for (; pos < message.length(); pos++) {
      char c = message.charAt(pos);
      // Get the relative offset of the ASCII letter (in the range 0-25) ignoring whether it's
      // upper or lower case. Using this unsigned value avoids multiple range checks in a tight
      // loop.
      int alpha = (char) ((c & ~0x20) - 'A');
      if (alpha < 26) {
        return pos;
      }
    }
    throw ParseException.withStartPosition("unterminated parameter", message, termStart);
  }

  /**
   * Unescapes the characters in the sub-string {@code s.substring(start, end)} according to
   * printf style formatting rules.
   */
  // VisibleForTesting
  static void unescapePrintf(StringBuilder out, String message, int start, int end) {
    int pos = start;
    while (pos < end) {
      if (message.charAt(pos++) != '%') {
        continue;
      }
      if (pos == end) {
        // Ignore unexpected trailing '%'.
        break;
      }
      char chr = message.charAt(pos);
      if (chr == '%') {
        // Append the section up to and including the first '%'.
        out.append(message, start, pos);
      } else if (chr == 'n') {
        // %n encountered, rewind one position to not emit leading '%' and emit newline.
        out.append(message, start, pos - 1);
        out.append(SYSTEM_NEWLINE);
      } else {
        // A single unescaped '%' is ignored and left in the output as-is.
        continue;
      }
      // Increment the position and reset the start point after the last processed character.
      start = ++pos;
    }
    // Append the last section (if it's non empty).
    if (start < end) {
      out.append(message, start, end);
    }
  }
}
