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

/**
 * An enum representing the printf-like formatting characters that must be supported by all logging
 * backends. It is important to note that while backends must accept any of these format specifiers,
 * they are not obliged to implement all specified formatting behavior.
 * <p>
 * The default term formatter takes care of supporting all these options when expressed in their
 * normal '%X' form (including flags, width and precision). Custom messages parsers must convert
 * arguments into one of these forms before passing then through to the backend.
 */
public enum FormatChar {
  /**
   * Formats the argument in a manner specific to the chosen logging backend. In many cases this
   * will be equivalent to using {@code STRING}, but it allows backend implementations to log more
   * structured representations of known types.
   * <p>
   * This is a non-numeric format with an upper-case variant.
   */
  STRING('s', FormatType.GENERAL, "-#", true /* upper-case variant */),

  /**
   * Formats the argument as a boolean.
   * <p>
   * This is a non-numeric format with an upper-case variant.
   */
  BOOLEAN('b', FormatType.BOOLEAN, "-", true /* upper-case variant */),

  /**
   * Formats a Unicode code-point. This formatting rule can be applied to any character or integral
   * numeric value, providing that {@link Character#isValidCodePoint(int)} returns true. Note that
   * if the argument cannot be represented losslessly as an integer, it must be considered invalid.
   * <p>
   * This is a non-numeric format with an upper-case variant.
   */
  CHAR('c', FormatType.CHARACTER, "-", true /* upper-case variant */),

  /**
   * Formats the argument as a decimal integer.
   * <p>
   * This is a numeric format.
   */
  DECIMAL('d', FormatType.INTEGRAL, "-0+ ,(", false  /* lower-case only */),

  /**
   * Formats the argument as an unsigned octal integer.
   * <p>
   * This is a numeric format.
   * <p>
   * '(' is only supported for {@link java.math.BigInteger} or {@link java.math.BigDecimal}
   */
  OCTAL('o', FormatType.INTEGRAL, "-#0(", false  /* lower-case only */),

  /**
   * Formats the argument as an unsigned hexadecimal integer.
   * <p>
   * This is a numeric format with an upper-case variant.
   * <p>
   * '(' is only supported for {@link java.math.BigInteger} or {@link java.math.BigDecimal}
   */
  HEX('x', FormatType.INTEGRAL, "-#0(", true /* upper-case variant */),

  /**
   * Formats the argument as a signed decimal floating value.
   * <p>
   * This is a numeric format.
   */
  FLOAT('f', FormatType.FLOAT, "-#0+ ,(", false  /* lower-case only */),

  /**
   * Formats the argument using computerized scientific notation.
   * <p>
   * This is a numeric format with an upper-case variant.
   */
  EXPONENT('e', FormatType.FLOAT, "-#0+ (", true /* upper-case variant */),

  /**
   * Formats the argument using general scientific notation.
   * <p>
   * This is a numeric format with an upper-case variant.
   */
  GENERAL('g', FormatType.FLOAT, "-0+ ,(", true /* upper-case variant */),

  /**
   * Formats the argument using hexadecimal exponential form. This formatting option is primarily
   * useful when debugging issues with the precise bit-wise representation of doubles because no
   * rounding of the value takes place.
   * <p>
   * This is a numeric format with an upper-case variant.
   */
  // Note: This could be optimized with Double.toHexString() but this parameter is hardly ever used.
  EXPONENT_HEX('a', FormatType.FLOAT, "-#0+ ", true /* upper-case variant */);

  // Returns the numeric index [0-25] of a given ASCII letter (upper or lower case). If the given
  // value is not an ASCII letter, the returned value is not in the range 0-25.
  private static int indexOf(char letter) {
    return (letter | 0x20) - 'a';
  }

  // Returns whether a given ASCII letter is lower case.
  private static boolean isLowerCase(char letter) {
    return (letter & 0x20) != 0;
  }

  // A direct mapping from character offset to FormatChar instance. Have all 26 letters accounted
  // for because we know that the caller has already checked that this is an ASCII letter.
  // This mapping needs to be fast as it's called for every argument in every log message.
  private static final FormatChar[] MAP = new FormatChar[26];
  static {
    for (FormatChar fc : values()) {
      MAP[indexOf(fc.getChar())] = fc;
    }
  }

  /**
   * Returns the FormatChar instance associated with the given printf format specifier. If the
   * given character is not an ASCII letter, a runtime exception is thrown.
   */
  public static FormatChar of(char c) {
    // Get from the map by converting the char to lower-case (which is the most common case by far).
    // If the given value wasn't an ASCII letter then the index will be out-of-range, but when
    // called by the parser, it's always guaranteed to be an ASCII letter (but perhaps not a valid
    // format character).
    FormatChar fc = MAP[indexOf(c)];
    if (isLowerCase(c)) {
      // If we were given a lower case char to find, we're done (even if the result is null).
      return fc;
    }
    // Otherwise handle the case where we found a lower-case format char but no upper-case one.
    return (fc != null && fc.hasUpperCaseVariant()) ? fc : null;
  }

  private final char formatChar;
  private final FormatType type;
  private final int allowedFlags;
  private final String defaultFormatString;

  FormatChar(char c, FormatType type, String allowedFlagChars, boolean hasUpperCaseVariant) {
    this.formatChar = c;
    this.type = type;
    this.allowedFlags = FormatOptions.parseValidFlags(allowedFlagChars, hasUpperCaseVariant);
    this.defaultFormatString = "%" + c;
  }

  /**
   * Returns the lower-case printf style formatting character.
   * <p>
   * Note that as this enumeration is not a subset of any other common formatting syntax, it is not
   * safe to assume that this character can be used to construct a formatting string to pass to
   * other formatting libraries.
   */
  public char getChar() {
    return formatChar;
  }

  /** Returns the general format type for this character. */
  public FormatType getType() {
    return type;
  }

  /**
   * Returns the allowed flag characters as a string. This is package private to hide the precise
   * implementation of how we parse and manage formatting options.
   */
  int getAllowedFlags() {
    return allowedFlags;
  }

  private boolean hasUpperCaseVariant() {
    return (allowedFlags & FormatOptions.FLAG_UPPER_CASE) != 0;
  }

  public String getDefaultFormatString() {
    return defaultFormatString;
  }
}
