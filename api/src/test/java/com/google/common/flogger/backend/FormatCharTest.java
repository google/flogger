/*
 * Copyright (C) 2013 The Flogger Authors.
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

import static com.google.common.flogger.backend.FormatChar.CHAR;
import static com.google.common.flogger.backend.FormatChar.DECIMAL;
import static com.google.common.flogger.backend.FormatChar.EXPONENT;
import static com.google.common.flogger.backend.FormatChar.EXPONENT_HEX;
import static com.google.common.flogger.backend.FormatChar.FLOAT;
import static com.google.common.flogger.backend.FormatChar.GENERAL;
import static com.google.common.flogger.backend.FormatChar.HEX;
import static com.google.common.flogger.backend.FormatChar.OCTAL;
import static com.google.common.flogger.backend.FormatChar.STRING;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FormatCharTest {

  private static void assertFormatType(FormatType type, FormatChar... formatChars) {
    for (FormatChar fc : formatChars) {
      assertThat(fc.getType()).isSameAs(type);
    }
  }

  private static void assertFlags(
      FormatChar formatChar, String allowedFlagChars, boolean hasUpperCase) {
    assertThat(formatChar.getAllowedFlags())
        .isEqualTo(FormatOptions.parseValidFlags(allowedFlagChars, hasUpperCase));
  }

  @Test
  public void testTypes() {
    assertFormatType(FormatType.GENERAL, STRING);
    assertFormatType(FormatType.CHARACTER, CHAR);
    assertFormatType(FormatType.INTEGRAL, DECIMAL, HEX, OCTAL);
    assertFormatType(FormatType.FLOAT, FLOAT, GENERAL, EXPONENT, EXPONENT_HEX);
  }

  @Test
  public void testCase() {
    // Grouped by similar allowed flags.
    assertFlags(FormatChar.STRING, "-#", true);

    assertFlags(FormatChar.BOOLEAN, "-", true);
    assertFlags(FormatChar.CHAR, "-", true);

    assertFlags(FormatChar.DECIMAL, "-0+ ,", false);
    assertFlags(FormatChar.GENERAL, "-0+ ,", true);

    assertFlags(FormatChar.HEX, "-#0", true);
    assertFlags(FormatChar.OCTAL, "-#0", false);

    assertFlags(FormatChar.FLOAT, "-#0+ ,", false);

    assertFlags(FormatChar.EXPONENT, "-#0+ ", true);
    assertFlags(FormatChar.EXPONENT_HEX, "-#0+ ", true);
  }

  @Test
  public void testAppendPrintfBadOptions() {
    assertThat(parseOptions("#016").areValidFor(STRING)).isFalse();
    assertThat(parseOptions("10.5").areValidFor(DECIMAL)).isFalse();
    assertThat(parseOptions(",").areValidFor(EXPONENT)).isFalse();
    assertThat(parseOptions("#").areValidFor(GENERAL)).isFalse();
    assertThat(parseOptions(" ").areValidFor(OCTAL)).isFalse();
    // Left alignment or zero padding must have a width.
    assertThat(parseOptions("-").areValidFor(DECIMAL)).isFalse();
    assertThat(parseOptions("0").areValidFor(HEX)).isFalse();
  }

  private static FormatOptions parseOptions(String s) {
    try {
      return FormatOptions.parse(s, 0, s.length(), false /* lower case - ignored */);
    } catch (ParseException parseException) {
      throw new RuntimeException(parseException);
    }
  }
}
