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

import com.google.common.flogger.parameter.BraceStyleParameter;

/**
 * Default implementation of the brace style message parser. Note that while the underlying parsing
 * mechanism supports the more general "{n,xxx}" form for brace format style logging, the default
 * message parser is currently limited to simple indexed place holders (e.g. "{0}"). This class
 * could easily be extended to support these trailing format specifiers.
 * <p>
 * Note also that the implicit place holder syntax used by Log4J (i.e. "{}") is not currently
 * supported, however this may change. Currently an unescaped "{}" term in a log message will cause
 * a parse error, so adding support for it should not be an issue.
 */
public class DefaultBraceStyleMessageParser extends BraceStyleMessageParser {
  private static final BraceStyleMessageParser INSTANCE = new DefaultBraceStyleMessageParser();

  public static BraceStyleMessageParser getInstance() {
    return INSTANCE;
  }

  private DefaultBraceStyleMessageParser() {}

  @Override
  public void parseBraceFormatTerm(
      MessageBuilder<?> builder,
      int index,
      String message,
      int termStart,
      int formatStart,
      int termEnd)
      throws ParseException {

    if (formatStart != -1) {
      // Specify the optional trailing part including leading ':' but excluding trailing '}'.
      throw ParseException.withBounds(
          "the default brace style parser does not allow trailing format specifiers",
          message,
          formatStart - 1,
          termEnd - 1);
    }
    builder.addParameter(termStart, termEnd, BraceStyleParameter.of(index));
  }
}
