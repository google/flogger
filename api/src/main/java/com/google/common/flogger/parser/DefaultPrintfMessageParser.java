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

import static com.google.common.flogger.backend.FormatOptions.FLAG_LEFT_ALIGN;
import static com.google.common.flogger.backend.FormatOptions.FLAG_UPPER_CASE;

import com.google.common.flogger.backend.FormatChar;
import com.google.common.flogger.backend.FormatOptions;
import com.google.common.flogger.parameter.DateTimeFormat;
import com.google.common.flogger.parameter.DateTimeParameter;
import com.google.common.flogger.parameter.Parameter;
import com.google.common.flogger.parameter.ParameterVisitor;
import com.google.common.flogger.parameter.SimpleParameter;

/**
 * Default implementation of the printf message parser. This parser supports all the place-holders
 * available in {@code String#format} but can be extended, if desired, for additional behavior
 * For consistency it is recommended, but not required, that custom printf parsers always extend
 * from this class.
 * <p>
 * This class is immutable and thread safe (and any subclasses must also be so).
 */
public class DefaultPrintfMessageParser extends PrintfMessageParser {
  private static final PrintfMessageParser INSTANCE = new DefaultPrintfMessageParser();

  public static PrintfMessageParser getInstance() {
    return INSTANCE;
  }

  private DefaultPrintfMessageParser() {}

  @Override
  public int parsePrintfTerm(
      MessageBuilder<?> builder,
      int index,
      String message,
      int termStart,
      int specStart,
      int formatStart)
      throws ParseException {

    // Assume terms are single characters.
    int termEnd = formatStart + 1;
    // This _must_ be an ASCII letter representing printf-like specifier (but need not be valid).
    char typeChar = message.charAt(formatStart);
    boolean isUpperCase = (typeChar & 0x20) == 0;
    FormatOptions options = FormatOptions.parse(message, specStart, formatStart, isUpperCase);

    Parameter parameter;
    FormatChar formatChar = FormatChar.of(typeChar);
    if (formatChar != null) {
      if (!options.areValidFor(formatChar)) {
        throw ParseException.withBounds("invalid format specifier", message, termStart, termEnd);
      }
      parameter = SimpleParameter.of(index, formatChar, options);
    } else if (typeChar == 't' || typeChar == 'T') {
      if (!options.validate(FLAG_LEFT_ALIGN | FLAG_UPPER_CASE, false)) {
        throw ParseException.withBounds(
            "invalid format specification", message, termStart, termEnd);
      }
      // Time/date format terms have an extra character in them.
      termEnd += 1;
      if (termEnd > message.length()) {
        throw ParseException.atPosition("truncated format specifier", message, termStart);
      }
      DateTimeFormat dateTimeFormat = DateTimeFormat.of(message.charAt(formatStart + 1));
      if (dateTimeFormat == null) {
        throw ParseException.atPosition("illegal date/time conversion", message, formatStart + 1);
      }
      parameter = DateTimeParameter.of(dateTimeFormat, options, index);
    } else if (typeChar == 'h' || typeChar == 'H') {
      // %h/%H is a legacy format we want to support for syntax compliance with String.format()
      // but which we don't need to support in the backend.
      if (!options.validate(FLAG_LEFT_ALIGN | FLAG_UPPER_CASE, false)) {
        throw ParseException.withBounds(
            "invalid format specification", message, termStart, termEnd);
      }
      parameter = wrapHexParameter(options, index);
    } else {
      throw ParseException.withBounds(
          "invalid format specification", message, termStart, formatStart + 1);
    }
    builder.addParameter(termStart, termEnd, parameter);
    return termEnd;
  }

  // Static method so the anonymous synthetic parameter is static, rather than an inner class.
  private static Parameter wrapHexParameter(final FormatOptions options, int index) {
    // %h / %H is really just %x / %X on the hashcode.
    return new Parameter(options, index) {
      @Override
      protected void accept(ParameterVisitor visitor, Object value) {
        visitor.visit(value.hashCode(), FormatChar.HEX, getFormatOptions());
      }

      @Override
      public String getFormat() {
        return options.shouldUpperCase() ? "%H" : "%h";
      }
    };
  }
}
