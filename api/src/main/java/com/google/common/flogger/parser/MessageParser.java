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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.LogFormat;

/**
 * Base class from which any specific message parsers are derived (ie, {@link PrintfMessageParser}
 * and {@link BraceStyleMessageParser}).
 * <p>
 * This class cannot be extended directly outside of this package because there are strong
 * assumptions elsewhere in the code regarding which {@link LogFormat}s exist.
 */
public abstract class MessageParser {
  /**
   * The maximum allowed index (this should correspond to the MAX_ALLOWED_WIDTH in
   * {@link com.google.common.flogger.backend.FormatOptions FormatOptions} because at times it is
   * ambiguous as to which is being parsed).
   */
  public static final int MAX_ARG_COUNT = 1000000;

  private final LogFormat format;

  /**
   * Package access only because it's not feasible to extend this class without adding a new
   * {@link LogFormat}, which would require many changes elsewhere in the code and potentially
   * cause incompatibilities with existing backend implementations.
   */
  MessageParser(LogFormat format) {
    this.format = checkNotNull(format, "log format");
  }

  /**
   * Abstract parse method implemented by specific subclasses to modify parsing behavior.
   * <p>
   * Note that when extending parsing behavior, it is expected that specific parsers such as
   * {@link DefaultPrintfMessageParser} or {@link DefaultBraceStyleMessageParser} will be
   * sub-classed. Extending this class directly is only necessary when an entirely new type of
   * format needs to be supported (which should be extremely rare).
   * <p>
   * Implementations of this method are required to invoke the
   * {@link MessageBuilder#addParameterImpl} method of the supplied builder once for each
   * parameter place-holder in the message.
   */
  protected abstract <T> void parseImpl(MessageBuilder<T> builder) throws ParseException;

  /**
   * Appends the unescaped literal representation of the given message string (assumed to be escaped
   * according to this parser's escaping rules). This method is designed to be invoked from a
   * callback method in a {@link MessageBuilder} instance.
   *
   * @param out the destination into which to append characters
   * @param message the escaped log message
   * @param start the start index (inclusive) in the log message
   * @param end the end index (exclusive) in the log message
   */
  public abstract void unescape(StringBuilder out, String message, int start, int end);
}
