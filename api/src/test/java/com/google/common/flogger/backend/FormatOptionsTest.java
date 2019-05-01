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

package com.google.common.flogger.backend;

import static com.google.common.flogger.backend.FormatOptions.ALL_FLAGS;
import static com.google.common.flogger.backend.FormatOptions.FLAG_LEFT_ALIGN;
import static com.google.common.flogger.backend.FormatOptions.FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES;
import static com.google.common.flogger.backend.FormatOptions.FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES;
import static com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_ALT_FORM;
import static com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_GROUPING;
import static com.google.common.flogger.backend.FormatOptions.FLAG_SHOW_LEADING_ZEROS;
import static com.google.common.flogger.backend.FormatOptions.FLAG_UPPER_CASE;
import static com.google.common.flogger.backend.FormatOptions.UNSET;
import static com.google.common.flogger.testing.FormatOptionsSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.flogger.parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FormatOptionsTest {
  @Test
  public void testDefaultSpec() {
    assertThat(FormatOptions.getDefault()).hasWidth(-1);
    assertThat(FormatOptions.getDefault()).hasPrecision(-1);
    assertThat(FormatOptions.getDefault()).hasNoFlags();
  }

  @Test
  public void testParsingDefault() throws ParseException {
    assertThat(FormatOptions.parse("%x", 1, 1, false)).isSameInstanceAs(FormatOptions.getDefault());
    // Upper-case options are not the same as the default, but are the same if case is filtered out.
    FormatOptions upperDefault = FormatOptions.parse("%X", 1, 1, true);
    assertThat(upperDefault).isNotSameInstanceAs(FormatOptions.getDefault());
  }

  @Test
  public void testManyFlags() throws ParseException {
    FormatOptions options = FormatOptions.parse("%-0#+ (,x", 1, 8, true);
    assertThat(options).hasWidth(-1);
    assertThat(options).hasPrecision(-1);
    assertThat(options.getFlags()).isEqualTo(ALL_FLAGS);
  }

  @Test
  public void testBadFlags() {
    try {
      // not a flag
      FormatOptions.parse("%", 0, 1, false);
      fail("expected ParseException");
    } catch (ParseException expected) {
    }
    try {
      // duplicate flags
      FormatOptions.parse("-#-", 0, 3, false);
      fail("expected ParseException");
    } catch (ParseException expected) {
    }
  }

  @Test
  public void testWidth() throws ParseException {
    FormatOptions options = FormatOptions.parse("%1234x", 1, 5, false);
    assertThat(options).hasWidth(1234);
    assertThat(options).hasPrecision(-1);
    assertThat(options).hasNoFlags();
  }

  @Test
  public void testWidthTooLarge() throws ParseException {
    FormatOptions options = FormatOptions.parse("%999999x", 1, 7, false);
    assertThat(options).hasWidth(999999);
    assertThat(options).hasPrecision(-1);
    assertThat(options).hasNoFlags();
    try {
      FormatOptions.parse("%1000000x", 1, 8, false);
      fail("expected ParseException");
    } catch (ParseException expected) {
    }
  }

  @Test
  public void testPrecision() throws ParseException {
    FormatOptions options = FormatOptions.parse("%.1234x", 1, 6, false);
    assertThat(options).hasWidth(-1);
    assertThat(options).hasPrecision(1234);
    assertThat(options).hasNoFlags();
  }

  @Test
  public void testPrecisionTooLarge() throws ParseException {
    FormatOptions options = FormatOptions.parse("%.999999x", 1, 8, false);
    assertThat(options).hasWidth(-1);
    assertThat(options).hasPrecision(999999);
    assertThat(options).hasNoFlags();
    try {
      FormatOptions.parse("%.1000000x", 2, 9, false);
      fail("expected ParseException");
    } catch (ParseException expected) {
    }
  }

  @Test
  public void testValidate() {
    FormatOptions options = parse("-#,123.456", false);
    int givenFlags = FLAG_LEFT_ALIGN | FLAG_SHOW_ALT_FORM | FLAG_SHOW_GROUPING;

    // Allow all flags and precision (should always return true).
    assertThat(options.validate(ALL_FLAGS, true)).isTrue();
    // Still ok if limit allowed flags to those present
    assertThat(options.validate(givenFlags, true)).isTrue();
    // Fails if disallow precision.
    assertThat(options.validate(givenFlags, false)).isFalse();
    // Fails if disallow one given flag.
    assertThat(options.validate(FLAG_LEFT_ALIGN | FLAG_SHOW_ALT_FORM, true)).isFalse();
  }

  @Test
  public void testValidateLikeGeneralType() throws ParseException {
    FormatOptions options = FormatOptions.parse("-123", 0, 4, false);
    assertThat(options).areValidFor(FormatChar.FLOAT);
    assertThat(options).areValidFor(FormatChar.DECIMAL);
    assertThat(options).areValidFor(FormatChar.OCTAL);
    assertThat(options).areValidFor(FormatChar.STRING);
  }

  @Test
  public void testValidateLikeFloatingPoint() {
    FormatOptions options = parse("-,123.456", false);
    assertThat(options).areValidFor(FormatChar.FLOAT);
    // Decimal does not permit precision.
    assertThat(options).areNotValidFor(FormatChar.DECIMAL);
    // Octal does not permit grouping or negative flags.
    assertThat(options).areNotValidFor(FormatChar.OCTAL);
    // String is not a numeric type.
    assertThat(options).areNotValidFor(FormatChar.STRING);
  }

  @Test
  public void testValidateLikeDecimal() {
    FormatOptions options = parse("-,123", false);
    assertThat(options).areValidFor(FormatChar.FLOAT);
    assertThat(options).areValidFor(FormatChar.DECIMAL);
    // Octal does not permit grouping or negative flags.
    assertThat(options).areNotValidFor(FormatChar.OCTAL);
    // String is not a numeric type.
    assertThat(options).areNotValidFor(FormatChar.STRING);
  }

  @Test
  public void testValidateLikeInconsistentFlags() {
    FormatOptions options;
    // Prefixing plus and space for negative values is always incompatible for all formats.
    options = parse("+ ", false);
    for (FormatChar fc : FormatChar.values()) {
      assertThat(options).areNotValidFor(fc);
    }
    // Left alignment and zero padding are always incompatible for all formats.
    options = parse("-0", false);
    for (FormatChar fc : FormatChar.values()) {
      assertThat(options).areNotValidFor(fc);
    }
  }

  @Test
  public void testDefaultSpecValidatesLikeEverything() {
    for (FormatChar fc : FormatChar.values()) {
      assertThat(FormatOptions.getDefault()).areValidFor(fc);
    }
  }

  @Test
  public void testAppendPrintfOptions() {
    StringBuilder out = new StringBuilder();
    FormatOptions options = parse("+-( #0,123.456", false);
    options.appendPrintfOptions(out);
    assertThat(out.toString()).isEqualTo(" #(+,-0123.456");
  }

  @Test
  public void testFilter() {
    assertThat(FormatOptions.getDefault().filter(ALL_FLAGS, true, true)).isDefault();

    FormatOptions options = parse("+- #0,123.456", true);
    assertThat(options.filter(0, false, false)).isDefault();
    assertThat(options.filter(ALL_FLAGS, true, true)).isSameInstanceAs(options);

    int flags = FLAG_LEFT_ALIGN | FLAG_SHOW_ALT_FORM | FLAG_SHOW_GROUPING | FLAG_SHOW_LEADING_ZEROS;
    FormatOptions filtered = options.filter(flags, true, false);
    assertThat(filtered).shouldLeftAlign();
    assertThat(filtered).shouldShowAltForm();
    assertThat(filtered).shouldShowGrouping();
    assertThat(filtered).shouldShowLeadingZeros();
    assertThat(filtered).shouldntPrefixSpaceForPositiveValues();
    assertThat(filtered).shouldntPrefixPlusForPositiveValues();
    assertThat(filtered).hasWidth(123);
    assertThat(filtered).hasPrecision(UNSET);
    assertThat(filtered).shouldntUpperCase();

    // Flags incompatible with the first set.
    int otherFlags = FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES
        | FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES
        | FLAG_UPPER_CASE;
    filtered = options.filter(otherFlags, false, true);
    assertThat(filtered).shouldntLeftAlign();
    assertThat(filtered).shouldntShowAltForm();
    assertThat(filtered).shouldntShowGrouping();
    assertThat(filtered).shouldntShowLeadingZeros();
    assertThat(filtered).shouldPrefixSpaceForPositiveValues();
    assertThat(filtered).shouldPrefixPlusForPositiveValues();
    assertThat(filtered).hasWidth(UNSET);
    assertThat(filtered).hasPrecision(456);
    assertThat(filtered).shouldUpperCase();
  }

  // TODO: Look at using the equality tester for this (and other classes) that would benefit.

  @Test
  public void testEqualsAndHashCode() {
    FormatOptions options = parse("+-( #0,123.456", false);
    // Not the same as the default options.
    assertThat(options).isNotEqualTo(FormatOptions.getDefault());
    // Order of flags doesn't matter.
    assertThat(options).isEqualTo(parse(",0# (-+123.456", false));
    // Different flags matter.
    assertThat(options).isNotEqualTo(parse("123.456", false));
    // Different width matters.
    assertThat(options).isNotEqualTo(parse(",0# (-+999.456", false));
    // Different precision matters.
    assertThat(options).isNotEqualTo(parse(",0# (-+123.999", false));
    // Upper-case flag does matter.
    assertThat(options).isNotEqualTo(parse(",0# (-+123.456", true));

    assertThat(options.hashCode()).isNotEqualTo(FormatOptions.getDefault().hashCode());
    assertThat(options.hashCode()).isEqualTo(parse("+-( #0,123.456", false).hashCode());
  }

  private static FormatOptions parse(String s, boolean isUpperCase) {
    try {
      return FormatOptions.parse(s, 0, s.length(), isUpperCase);
    } catch (ParseException parseException) {
      throw new RuntimeException(parseException);
    }
  }
}
