/*
 * Copyright (C) 2020 The Flogger Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.flogger.parser.ParseException;
import com.google.common.flogger.testing.FakeLogData;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.FormatFlagsConversionMismatchException;
import java.util.Formattable;
import java.util.Formatter;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BaseMessageFormatterTest {
  @Test
  public void testBasicFormatting() {
    assertThat(formatPrintf("Hello World")).isEqualTo("Hello World");
    assertThat(formatPrintf("Hello %s", "World")).isEqualTo("Hello World");
    assertThat(formatPrintf("Hello %s", (Object) null)).isEqualTo("Hello null");
    assertThat(formatPrintf("Hello %%s")).isEqualTo("Hello %s");
    // With no arguments, log statements treat the value as a literal and don't escape.
    assertThat(formatLiteral("Hello %s")).isEqualTo("Hello %s");
  }

  @Test
  public void testBasicFormatting_errors() {
    assertThat(formatPrintf("Hello %s %s", "World"))
        .isEqualTo("Hello World [ERROR: MISSING LOG ARGUMENT]");
    assertThat(formatPrintf("Hello %d", "World"))
        .isEqualTo("Hello [INVALID: format=%d, type=java.lang.String, value=World]");
  }

  @Test
  public void testFormattable() {
    Formattable arg =
        new Formattable() {
          @Override
          public void formatTo(Formatter formatter, int flags, int width, int precision) {
            formatter.format((Locale) null, "[f=%d,w=%d,p=%d]", flags, width, precision);
          }
        };
    assertThat(formatPrintf("%s", arg)).isEqualTo("[f=0,w=-1,p=-1]");
    assertThat(formatPrintf("%100s", arg)).isEqualTo("[f=0,w=100,p=-1]");
    assertThat(formatPrintf("%.25s", arg)).isEqualTo("[f=0,w=-1,p=25]");
    assertThat(formatPrintf("%100.25s", arg)).isEqualTo("[f=0,w=100,p=25]");
    assertThat(formatPrintf("%-100s", arg)).isEqualTo("[f=1,w=100,p=-1]");
    assertThat(formatPrintf("%S", arg)).isEqualTo("[f=2,w=-1,p=-1]");
    assertThat(formatPrintf("%#s", arg)).isEqualTo("[f=4,w=-1,p=-1]");
    assertThat(formatPrintf("%-#32.16S", arg)).isEqualTo("[f=7,w=32,p=16]");
  }

  @Test
  public void testNumberFormatting() {
    // TODO: add more tests with other flags ',', ' ', '-', '+'
    assertThat(formatPrintf("%d", -123)).isEqualTo("-123");
    assertThat(formatPrintf("%d", -123L)).isEqualTo("-123");
    assertThat(formatPrintf("%G", -123f)).isEqualTo("-123.000");
    assertThat(formatPrintf("%e", -123f)).isEqualTo("-1.230000e+02");
    assertThat(formatPrintf("%f", -123f)).isEqualTo("-123.000000");
    assertThat(formatPrintf("%g", -123.456789)).isEqualTo("-123.457");
    assertThat(formatPrintf("%.6G", -123.456789)).isEqualTo("-123.457"); // Precision is ignored
    assertThat(formatPrintf("%.8E", -123.456789)).isEqualTo("-1.23456789E+02");
    assertThat(formatPrintf("%f", -123.456789)).isEqualTo("-123.456789");

    assertThat(formatPrintf("%(d", 123)).isEqualTo("123");
    assertThat(formatPrintf("%(d", -123)).isEqualTo("(123)");
    assertThat(formatPrintf("%(d", -123L)).isEqualTo("(123)");
    assertThat(formatPrintf("%(g", -123f)).isEqualTo("(123.000)");
    assertThat(formatPrintf("%(E", -123f)).isEqualTo("(1.230000E+02)");
    assertThat(formatPrintf("%(f", -123f)).isEqualTo("(123.000000)");
    assertThat(formatPrintf("%(.0f", -123f)).isEqualTo("(123)");
    assertThat(formatPrintf("%(4.10f", -123f)).isEqualTo("(123.0000000000)");
    assertThat(formatPrintf("%(1.2f", -123f)).isEqualTo("(123.00)");
    assertThat(formatPrintf("%(.2f", -123f)).isEqualTo("(123.00)");
    assertThat(formatPrintf("%(f", -123.0)).isEqualTo("(123.000000)");

    // Hex int and BigInteger
    assertThat(formatPrintf("%x", 123)).isEqualTo("7b");
    assertThat(formatPrintf("%X", -123)).isEqualTo("FFFFFF85");
    assertThat(formatPrintf("%x", BigInteger.valueOf(123))).isEqualTo("7b");
    assertThat(formatPrintf("%X", BigInteger.valueOf(-123))).isEqualTo("-7B");
    assertThat(formatPrintf("%(x", BigInteger.valueOf(-123))).isEqualTo("(7b)");
    assertThat(formatPrintf("%(x", BigInteger.valueOf(123))).isEqualTo("7b");

    // Octal ints and BigInteger
    assertThat(formatPrintf("%o", 123)).isEqualTo("173");
    assertThat(formatPrintf("%o", -123)).isEqualTo("37777777605");
    assertThat(formatPrintf("%o", BigInteger.valueOf(123))).isEqualTo("173");
    assertThat(formatPrintf("%o", BigInteger.valueOf(-123))).isEqualTo("-173");
    assertThat(formatPrintf("%(o", BigInteger.valueOf(-123))).isEqualTo("(173)");
    assertThat(formatPrintf("%(o", BigInteger.valueOf(123))).isEqualTo("173");

    // BigDecimal
    assertThat(formatPrintf("%f", BigDecimal.ONE)).isEqualTo("1.000000");
    assertThat(formatPrintf("%f", BigDecimal.valueOf(-1234.56789))).isEqualTo("-1234.567890");
    assertThat(formatPrintf("%g", BigDecimal.ONE)).isEqualTo("1.00000");
    assertThat(formatPrintf("%g", BigDecimal.valueOf(-123456789))).isEqualTo("-1.23457e+08");
    assertThat(formatPrintf("%G", BigDecimal.valueOf(-1234.56789))).isEqualTo("-1234.57");
    assertThat(formatPrintf("%G", BigDecimal.valueOf(-123456789))).isEqualTo("-1.23457E+08");
    assertThat(formatPrintf("%e", BigDecimal.valueOf(1234.56789))).isEqualTo("1.234568e+03");
    assertThat(formatPrintf("%E", BigDecimal.valueOf(-1234.56789))).isEqualTo("-1.234568E+03");
    assertThat(formatPrintf("%(f", BigDecimal.valueOf(-1234.56789))).isEqualTo("(1234.567890)");
    assertThat(formatPrintf("%(g", BigDecimal.valueOf(-1234.56789))).isEqualTo("(1234.57)");
    assertThat(formatPrintf("%(e", BigDecimal.valueOf(-1234.56789))).isEqualTo("(1.234568e+03)");

    // '#' tests
    assertThat(formatPrintf("%#o", -123)).isEqualTo("037777777605");
    assertThat(formatPrintf("%#x", 123)).isEqualTo("0x7b");
    assertThat(formatPrintf("%#X", 123)).isEqualTo("0X7B");
    assertThat(formatPrintf("%#.0f", 123.)).isEqualTo("123.");
  }

  @Test
  public void testInvalidFlags() {
    assertFormatFailure("%(s", 123);
    assertFormatFailure("%(b", 123);
    assertFormatFailure("%(s", -123);
    assertFormatFailure("%(b", -123);

    assertFormatFailure("%#h", "foo");
    assertFormatFailure("%#b", true);
    assertFormatFailure("%#d", 123);
    assertFormatFailure("%#g", BigDecimal.ONE);
  }

  @Test
  public void testFormatFlagsConversionMismatchException() {
    assertFormatFlagsConversionMismatchException("%(x", 123);
    assertFormatFlagsConversionMismatchException("%(o", 123);
    assertFormatFlagsConversionMismatchException("%(x", -123);
    assertFormatFlagsConversionMismatchException("%(o", -123);
  }

  private static String formatLiteral(Object value) {
    LogData logData = FakeLogData.of(value);
    StringBuilder out = new StringBuilder();
    BaseMessageFormatter.appendFormattedMessage(logData, out);
    return out.toString();
  }

  private static String formatPrintf(String msg, Object... args) {
    LogData logData = FakeLogData.withPrintfStyle(msg, args);
    StringBuilder out = new StringBuilder();
    BaseMessageFormatter.appendFormattedMessage(logData, out);
    return out.toString();
  }

  private static void assertFormatFailure(String format, Object arg) {
    try {
      formatPrintf(format, arg);
      fail("expected ParseException");
    } catch (ParseException expected) {
    }
  }

  private static void assertFormatFlagsConversionMismatchException(String format, Object arg) {
    try {
      formatPrintf(format, arg);
      fail("expected FormatFlagsConversionMismatchException");
    } catch (FormatFlagsConversionMismatchException expected) {
    }
  }
}
