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

import static com.google.common.flogger.parser.ParserTestUtil.assertParse;
import static com.google.common.flogger.parser.ParserTestUtil.assertParseError;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.flogger.parser.ParserTestUtil.FakeParameter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrintfMessageParserTest {
  /** Fake parser that simply generates detail strings of the terms is was asked to parse. */
  private static final MessageParser PARSER = new PrintfMessageParser() {
    @Override
    int parsePrintfTerm(
        MessageBuilder<?> builder,
        int index,
        String message,
        int termStart,
        int specStart,
        int formatStart) {
      StringBuilder detail = new StringBuilder();
      if (formatStart > specStart) {
        detail.append(message, specStart, formatStart).append(':');
      }
      detail.append(message.charAt(formatStart));
      // Assume in tests we are not considering multi-character format specifiers such as "%Tc"
      int termEnd = formatStart + 1;
      builder.addParameter(termStart, termEnd, new FakeParameter(index, detail.toString()));
      return termEnd;
    }
  };

  @Test
  public void testPrintfNextTerm() throws ParseException {
    assertThat(PrintfMessageParser.nextPrintfTerm("", 0)).isEqualTo(-1);
    assertThat(PrintfMessageParser.nextPrintfTerm("%X", 0)).isEqualTo(0);
    assertThat(PrintfMessageParser.nextPrintfTerm("Hello %X World %X", 0)).isEqualTo(6);
    assertThat(PrintfMessageParser.nextPrintfTerm("Hello %X World %X", 6)).isEqualTo(6);
    assertThat(PrintfMessageParser.nextPrintfTerm("Hello %X World %X", 7)).isEqualTo(15);
    assertThat(PrintfMessageParser.nextPrintfTerm("Hello %% World %X", 0)).isEqualTo(15);
    assertThat(PrintfMessageParser.nextPrintfTerm("Hello %X World %X", 16)).isEqualTo(-1);
  }

  @Test
  public void testPrintfNextTermFails() {
    try {
      int unused = PrintfMessageParser.nextPrintfTerm("Hello %", 0);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("[%]");
    }
  }

  @Test
  public void testParse() {
    assertParse(PARSER, "Hello World");
    assertParse(PARSER, "Hello %A %B %C World", "0:A", "1:B", "2:C");
    assertParse(PARSER, "Hello %1$A %2$B %3$C World", "0:A", "1:B", "2:C");
    assertParse(PARSER, "Hello %2$A %B %C %1$D World", "1:A", "0:B", "1:C", "0:D");
    assertParse(PARSER, "Hello %A %<B %<C World", "0:A", "0:B", "0:C");
    assertParse(PARSER, "Hello %???X World", "0:???:X");
    assertParse(PARSER, "%%%A%%X%%%B%%", "0:A", "1:B");
  }

  @Test
  public void testParsePrintfError() {
    assertParseError(PARSER, "%", "[%]");
    assertParseError(PARSER, "Hello %", "[%]");
    // Unterminated parameter
    assertParseError(PARSER, "Hello %1", "[%1]");
    assertParseError(PARSER, "Hello %1$", "[%1$]");
    // Missing index
    assertParseError(PARSER, "Hello %$ World", "[%$]");
    // Leading zeros
    assertParseError(PARSER, "Hello %01$X World", "[%01$]");
    // Index too large
    assertParseError(PARSER, "Hello %1000000X World", "[%1000000]");
    // Printf indices are 1-based
    assertParseError(PARSER, "Hello %0$X World", "[%0$]");
    // Relative indexing cannot come first.
    assertParseError(PARSER, "Hello %<X World", "[%<]");
    // Unexpected flag or missing term character
    assertParseError(PARSER, "Hello %????", "[%????]");
    assertParseError(PARSER, "Hello %X %<", "[%<]");
    // Gaps in term indices is a parse error, report the first argument index not referenced.
    assertParseError(PARSER, "Hello %X %100$X World", "first missing index=1");
  }

  @Test
  public void testGetSafeSystemNewline() {
    // This should pass even if "line.separator" is set to something else.
    String nl = PrintfMessageParser.getSafeSystemNewline();
    assertThat(nl).isAnyOf("\n", "\r", "\r\n");
  }

  @Test
  public void testUnescapePrintfSupportsNewline() {
    String nl = PrintfMessageParser.getSafeSystemNewline();
    assertThat(unescapePrintf("%n")).isEqualTo(nl);
    assertThat(unescapePrintf("Hello %n World")).isEqualTo("Hello " + nl + " World");
    assertThat(unescapePrintf("Hello World %n")).isEqualTo("Hello World " + nl);
    assertThat(unescapePrintf("%n%n%%n%n")).isEqualTo(nl + nl + "%n" + nl);
  }

  @Test
  public void testUnescapePrintf() {
    assertThat(unescapePrintf("")).isEqualTo("");
    assertThat(unescapePrintf("Hello World")).isEqualTo("Hello World");
    assertThat(unescapePrintf("Hello %% World")).isEqualTo("Hello % World");
    assertThat(unescapePrintf("Hello %%%% World")).isEqualTo("Hello %% World");
    assertThat(unescapePrintf("%% 'Hello {%%}{%%} World' %%"))
        .isEqualTo("% 'Hello {%}{%} World' %");
  }

  @Test
  public void testUnescapePrintfIgnoresErrors() {
    assertThat(unescapePrintf("Hello % World")).isEqualTo("Hello % World");
    assertThat(unescapePrintf("Hello %")).isEqualTo("Hello %");
  }

  private static String unescapePrintf(String message) {
    StringBuilder out = new StringBuilder();
    PrintfMessageParser.unescapePrintf(out, message, 0, message.length());
    return out.toString();
  }
}
