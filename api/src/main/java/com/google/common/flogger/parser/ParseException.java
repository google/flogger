/*
 * Copyright (C) 2012 The Flogger Authors.
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
 * The exception that should be thrown whenever parsing of a log message fails. This exception must
 * not be thrown outside of template parsing.
 */
public final class ParseException extends RuntimeException {
  // The prefix/suffix to show when an error snippet is truncated (eg, "...ello [%Q] Worl...").
  // If the snippet starts or ends the message then no ellipsis is shown (eg, "...ndex=[%Q]").
  private static final String ELLIPSIS = "...";
  // The length of the snippet to show before and after the error. Fewer characters will be shown
  // if the error is near the start/end of the log message and more characters will be shown if
  // adding the ellipsis would have made things longer. The maximum prefix/suffix of the snippet
  // is (SNIPPET_LENGTH + ELLIPSIS.length()).
  private static final int SNIPPET_LENGTH = 5;

  /**
   * Creates a new parse exception for situations in which both the start and end positions of the
   * error are known.
   *
   * @param errorMessage the user error message.
   * @param logMessage the original log message.
   * @param start the index of the first character in the invalid section of the log message.
   * @param end the index after the last character in the invalid section of the log message.
   * @return the parser exception.
   */
  public static ParseException withBounds(
      String errorMessage, String logMessage, int start, int end) {
    return new ParseException(msg(errorMessage, logMessage, start, end), logMessage);
  }

  /**
   * Creates a new parse exception for situations in which the position of the error is known.
   *
   * @param errorMessage the user error message.
   * @param logMessage the original log message.
   * @param position the index of the invalid character in the log message.
   * @return the parser exception.
   */
  public static ParseException atPosition(String errorMessage, String logMessage, int position) {
    return new ParseException(msg(errorMessage, logMessage, position, position + 1), logMessage);
  }

  /**
   * Creates a new parse exception for situations in which only the start position of the error is
   * known.
   *
   * @param errorMessage the user error message.
   * @param logMessage the original log message.
   * @param start the index of the first character in the invalid section of the log message.
   * @return the parser exception.
   */
  public static ParseException withStartPosition(
      String errorMessage, String logMessage, int start) {
    return new ParseException(msg(errorMessage, logMessage, start, -1), logMessage);
  }

  /**
   * Creates a new parse exception for cases where position is not relevant.
   *
   * @param errorMessage the user error message.
   * @param logMessage the original log message.
   */
  public static ParseException generic(String errorMessage, String logMessage) {
    return new ParseException(errorMessage, logMessage);
  }

  private ParseException(String errorMessage, String logMessage) {
    super(errorMessage);
  }

  /** Helper to format a human readable error message for this exception. */
  private static String msg(String errorMessage, String logMessage, int errorStart, int errorEnd) {
    if (errorEnd < 0) {
      errorEnd = logMessage.length();
    }
    StringBuilder out = new StringBuilder(errorMessage).append(": ");
    if (errorStart > SNIPPET_LENGTH + ELLIPSIS.length()) {
      out.append(ELLIPSIS).append(logMessage, errorStart - SNIPPET_LENGTH, errorStart);
    } else {
      out.append(logMessage, 0, errorStart);
    }
    out.append('[').append(logMessage.substring(errorStart, errorEnd)).append(']');
    if (logMessage.length() - errorEnd > SNIPPET_LENGTH + ELLIPSIS.length()) {
      out.append(logMessage, errorEnd, errorEnd + SNIPPET_LENGTH).append(ELLIPSIS);
    } else {
      out.append(logMessage, errorEnd, logMessage.length());
    }
    return out.toString();
  }

  // Disable expensive stack analysis because the parse exception will contain everything it needs
  // to point the user at the proximal cause in the log message itself, and backends must always
  // wrap this in a LoggingException if they do throw it up into user code (not recommended).
  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
