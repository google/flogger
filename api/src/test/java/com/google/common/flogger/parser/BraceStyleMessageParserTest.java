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
public class BraceStyleMessageParserTest {
  /** Fake parser that simply generates detail strings of the terms is was asked to parse. */
  private static final MessageParser PARSER = new BraceStyleMessageParser() {
    @Override
    void parseBraceFormatTerm(
        MessageBuilder<?> builder,
        int index,
        String message,
        int termStart,
        int formatStart,
        int termEnd) {
      assertThat(message.charAt(termStart)).isEqualTo('{');
      assertThat(message.charAt(termEnd - 1)).isEqualTo('}');
      int indexEnd = (formatStart != -1) ? formatStart : termEnd;
      assertThat(Integer.parseInt(message.substring(termStart + 1, indexEnd - 1))).isEqualTo(index);
      String detail = "";
      if (formatStart != -1) {
        assertThat(message.charAt(formatStart - 1)).isEqualTo(',');
        detail = message.substring(formatStart, termEnd - 1);
      }
      builder.addParameter(termStart, termEnd, new FakeParameter(index, detail));
    }
  };

  @Test
  public void testBraceFormatNextTerm() throws ParseException {
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("", 0)).isEqualTo(-1);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("{", 0)).isEqualTo(0);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("Hello {0} World {1}", 0)).isEqualTo(6);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("Hello {0} World {1}", 6)).isEqualTo(6);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("Hello {0} World {1}", 7)).isEqualTo(16);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("Hello ''{0}'' World", 0)).isEqualTo(8);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("Hello {0} World {1}", 17))
        .isEqualTo(-1);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("Hello '{'0} World", 0)).isEqualTo(-1);
    assertThat(BraceStyleMessageParser.nextBraceFormatTerm("Hello '{0}' World", 0)).isEqualTo(-1);
  }

  @Test
  public void testBraceFormatNextTermFails() {
    try {
      int unused = BraceStyleMessageParser.nextBraceFormatTerm("Hello '", 0);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("[']");
    }
    try {
      int unused = BraceStyleMessageParser.nextBraceFormatTerm("Hello 'World", 0);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("['World]");
    }
  }

  @Test
  public void testParseBraceFormatNoTerms() throws ParseException {
    assertParse(PARSER, "Hello World");
    assertParse(PARSER, "Hello {0} {1} {2} World", "0", "1", "2");
    assertParse(PARSER, "Hello {1,XX} {0,YYY} World", "1:XX", "0:YYY");
    assertParse(PARSER, "Hello '{1}'=''{0}'' World", "0");
  }

  @Test
  public void testParseBraceFormatError() {
    assertParseError(PARSER, "'", "[']");
    assertParseError(PARSER, "Hello '", "[']");
    assertParseError(PARSER, "Hello ' World", "[' World]");
    // Unterminated parameter
    assertParseError(PARSER, "Hello {", "[{]");
    assertParseError(PARSER, "Hello {123", "[{123]");
    assertParseError(PARSER, "Hello {123,xyz", "[{123,xyz]");
    // Missing index
    assertParseError(PARSER, "Hello {} World", "[{}]");
    assertParseError(PARSER, "Hello {,} World", "[{,]");
    // Leading zeros
    assertParseError(PARSER, "Hello {00} World", "[00]");
    assertParseError(PARSER, "Hello {01} World", "[01]");
    // Index too large
    assertParseError(PARSER, "Hello {1000000} World", "[1000000]");
    // Malformed index
    assertParseError(PARSER, "Hello {123x} World", "[123x]");
  }

  @Test
  public void testUnescapeBraceFormat() {
    assertThat(unescapeBraceFormat("")).isEqualTo("");
    assertThat(unescapeBraceFormat("Hello World")).isEqualTo("Hello World");
    assertThat(unescapeBraceFormat("Hello '{}' World")).isEqualTo("Hello {} World");
    assertThat(unescapeBraceFormat("Hello \'{}\' World")).isEqualTo("Hello {} World");
    assertThat(unescapeBraceFormat("Hello \'{}' World")).isEqualTo("Hello {} World");
    assertThat(unescapeBraceFormat("Hello '' World")).isEqualTo("Hello ' World");
    assertThat(unescapeBraceFormat("Hello \'\' World")).isEqualTo("Hello ' World");
    assertThat(unescapeBraceFormat("Hello '\' World")).isEqualTo("Hello ' World");
    assertThat(unescapeBraceFormat("He'llo'' ''Wor'ld")).isEqualTo("Hello World");
    assertThat(unescapeBraceFormat("He'llo'\' \''Wor\'ld")).isEqualTo("Hello World");
  }

  @Test
  public void testUnescapeBraceFormatIgnoresErrors() {
    assertThat(unescapeBraceFormat("Hello '")).isEqualTo("Hello ");
    assertThat(unescapeBraceFormat("Hello \\'")).isEqualTo("Hello ");
  }

  private static String unescapeBraceFormat(String message) {
    StringBuilder out = new StringBuilder();
    BraceStyleMessageParser.unescapeBraceFormat(out, message, 0, message.length());
    return out.toString();
  }
}
