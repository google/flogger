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

import com.google.common.flogger.parser.ParseException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A structured representation of formatting options compatible with printf style formatting.
 * <p>
 * This class is immutable and thread safe.
 */
public final class FormatOptions {

  private static final int MAX_ALLOWED_WIDTH = 999999;
  private static final int MAX_ALLOWED_PRECISION = 999999;

  // WARNING: Never add any more flags here (flag encoding breaks if > 7 flags).
  private static final String FLAG_CHARS_ORDERED = " #(+,-0";
  private static final int MIN_FLAG_VALUE = ' ';
  private static final int MAX_FLAG_VALUE = '0';

  // For a flag character 'c' in [MIN_FLAG_VALUE, MAX_FLAG_VALUE] the flag index is stored in 3 bits
  // starting at bit-N, where N = (3 * (c - MIN_FLAG_VALUE)).
  private static final long ENCODED_FLAG_INDICES;

  static {
    long encoded = 0;
    for (int i = 0; i < FLAG_CHARS_ORDERED.length(); i++) {
      long n = (FLAG_CHARS_ORDERED.charAt(i) - MIN_FLAG_VALUE);
      encoded |= (i + 1L) << (3 * n);
    }
    ENCODED_FLAG_INDICES = encoded;
  }

  // Helper to decode a flag character which has already been determined to be in the range
  // [MIN_FLAG_VALUE, MAX_FLAG_VALUE]. For characters in this range, this function is identical to
  // "return FLAG_CHARS_ORDERED.indexOf(c)" but without any looping.
  private static int indexOfFlagCharacter(char c) {
    // TODO: Benchmark against "FLAG_CHARS_ORDERED.indexOf(c)" just to be sure.
    return (int) ((ENCODED_FLAG_INDICES >>> (3 * (c - MIN_FLAG_VALUE))) & 0x7L) - 1;
  }

  /**
   * A formatting flag which specifies that for signed numeric output, positive values should be
   * prefixed with an ASCII space ({@code ' '}). This corresponds to the {@code ' '} printf flag and
   * is valid for all signed numeric types.
   */
  public static final int FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES = (1 << 0);

  /**
   * A formatting flag which specifies that output should be shown in a type dependent alternate
   * form. This corresponds to the {@code '#'} printf flag and is valid for:
   * <ul>
   * <li>Octal (%o) and hexadecimal (%x, %X) formatting, where it specifies that the radix should be
   *     shown.
   * <li>Floating point (%f) and exponential (%e, %E, %a, %A) formatting, where it specifies that a
   *     decimal separator should always be shown.
   * </ul>
   */
  public static final int FLAG_SHOW_ALT_FORM = (1 << 1);

  /**
   * A formatting flag which specifies that for signed numeric output, negative values should be
   * surrounded by parentheses. This corresponds to the {@code '('} printf flag and is valid for all
   * signed numeric types.
   */
  public static final int FLAG_USE_PARENS_FOR_NEGATIVE_VALUES = (1 << 2);

  /**
   * A formatting flag which specifies that for signed numeric output, positive values should be
   * prefixed with an ASCII plus ({@code '+'}). This corresponds to the {@code '+'} printf flag and
   * is valid for all signed numeric types.
   */
  public static final int FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES = (1 << 3);

  /**
   * A formatting flag which specifies that for non-exponential, base-10, numeric output a grouping
   * separator (often a ',') should be used. This corresponds to the {@code ','} printf flag and
   * is valid for:
   * <ul>
   * <li>Decimal (%d) and unsigned (%u) formatting.
   * <li>Float (%f) and general scientific notation (%g, %G)
   * </ul>
   */
  public static final int FLAG_SHOW_GROUPING = (1 << 4);

  /**
   * A formatting flag which specifies that output should be left-aligned within the minimum
   * available width. This corresponds to the {@code '-'} printf flag and is valid for all
   * {@code FormatChar} instances, though it must be specified in conjunction with a width value.
   */
  public static final int FLAG_LEFT_ALIGN = (1 << 5);

  /**
   * A formatting flag which specifies that numeric output should be padding with leading zeros as
   * necessary to fill the minimum width. This corresponds to the {@code '0'} printf flag and is
   * valid for all numeric types, though it must be specified in conjunction with a width value.
   */
  public static final int FLAG_SHOW_LEADING_ZEROS = (1 << 6);

  /**
   * A formatting flag which specifies that output should be upper-cased after all other formatting.
   * This corresponds to having an upper-case format character and is valud for any type with an
   * upper case variant.
   */
  public static final int FLAG_UPPER_CASE = (1 << 7);

  /** A mask of all allowed formatting flags. Useful when filtering options via {@link #filter}. */
  public static final int ALL_FLAGS = 0xFF;

  /** The value used to specify that either width or precision were not specified. */
  public static final int UNSET = -1;

  private static final FormatOptions DEFAULT = new FormatOptions(0, UNSET, UNSET);

  /** Returns the default options singleton instance. */
  public static FormatOptions getDefault() {
    return DEFAULT;
  }

  /** Creates a options instance with the given values. */
  public static FormatOptions of(int flags, int width, int precision) {
    if (!checkFlagConsistency(flags, width != UNSET)) {
      throw new IllegalArgumentException("invalid flags: 0x" + Integer.toHexString(flags));
    }
    if ((width < 1 || width > MAX_ALLOWED_WIDTH) && width != UNSET) {
      throw new IllegalArgumentException("invalid width: " + width);
    }
    if ((precision < 0 || precision > MAX_ALLOWED_PRECISION) && precision != UNSET) {
      throw new IllegalArgumentException("invalid precision: " + precision);
    }
    return new FormatOptions(flags, width, precision);
  }

  /**
   * Parses a sub-sequence of a log message to extract and return its options. Note that callers
   * cannot rely on this method producing new instances each time it is called as caching of common
   * option values may occur.
   *
   * @param message the original log message in which the formatting options have been identified.
   * @param pos the index of the first character to parse.
   * @param end the index after the last character to be parsed.
   * @return the parsed options instance.
   * @throws ParseException if the specified sub-sequence of the string could not be parsed.
   */
  public static FormatOptions parse(String message, int pos, int end, boolean isUpperCase)
      throws ParseException {
    // It is vital that we shortcut parsing and return the default instance here (rather than just
    // creating a new instance with default values) because we check for it using '==' later).
    // Also, it saves us thousands of otherwise unnecessary allocations.
    if (pos == end && !isUpperCase) {
      return DEFAULT;
    }

    // STEP 1: Parse flag bits.
    int flags = isUpperCase ? FLAG_UPPER_CASE : 0;
    char c;
    while (true) {
      if (pos == end) {
        return new FormatOptions(flags, UNSET, UNSET);
      }
      c = message.charAt(pos++);
      if (c < MIN_FLAG_VALUE || c > MAX_FLAG_VALUE) {
        break;
      }
      int flagIdx = indexOfFlagCharacter(c);
      if (flagIdx < 0) {
        if (c == '.') {
          // Edge case of something like "%.2f" (precision but no width).
          return new FormatOptions(flags, UNSET, parsePrecision(message, pos, end));
        }
        throw ParseException.atPosition("invalid flag", message, pos - 1);
      }
      int flagBit = 1 << flagIdx;
      if ((flags & flagBit) != 0) {
        throw ParseException.atPosition("repeated flag", message, pos - 1);
      }
      flags |= flagBit;
    }

    // STEP 2: Parse width (which must start with [1-9]).
    // We know that c > MAX_FLAG_VALUE, which is really just '0', so (c >= 1)
    int widthStart = pos - 1;
    if (c > '9') {
      throw ParseException.atPosition("invalid flag", message, widthStart);
    }
    int width = c - '0';
    while (true) {
      if (pos == end) {
        return new FormatOptions(flags, width, UNSET);
      }
      c = message.charAt(pos++);
      if (c == '.') {
        return new FormatOptions(flags, width, parsePrecision(message, pos, end));
      }
      int n = (char) (c - '0');
      if (n >= 10) {
        throw ParseException.atPosition("invalid width character", message, pos - 1);
      }
      width = (width * 10) + n;
      if (width > MAX_ALLOWED_WIDTH) {
        throw ParseException.withBounds("width too large", message, widthStart, end);
      }
    }
  }

  private static int parsePrecision(String message, int start, int end) throws ParseException {
    if (start == end) {
      throw ParseException.atPosition("missing precision", message, start - 1);
    }
    int precision = 0;
    for (int pos = start; pos < end; pos++) {
      int n = (char) (message.charAt(pos) - '0');
      if (n >= 10) {
        throw ParseException.atPosition("invalid precision character", message, pos);
      }
      precision = (precision * 10) + n;
      if (precision > MAX_ALLOWED_PRECISION) {
        throw ParseException.withBounds("precision too large", message, start, end);
      }
    }
    // Check for many-zeros corner case (eg, "%.000f")
    if (precision == 0 && end != (start + 1)) {
      throw ParseException.withBounds("invalid precision", message, start, end);
    }
    return precision;
  }

  /** Internal helper method for creating a bit-mask from a string of valid flag characters. */
  static int parseValidFlags(String flagChars, boolean hasUpperVariant) {
    int flags = hasUpperVariant ? FLAG_UPPER_CASE : 0;
    for (int i = 0; i < flagChars.length(); i++) {
      int flagIdx = indexOfFlagCharacter(flagChars.charAt(i));
      if (flagIdx < 0) {
        throw new IllegalArgumentException("invalid flags: " + flagChars);
      }
      flags |= 1 << flagIdx;
    }
    return flags;
  }

  // NOTE: If we really cared about space we could encode everything into a single long.
  private final int flags;
  private final int width;
  private final int precision;

  private FormatOptions(int flags, int width, int precision) {
    this.flags = flags;
    this.width = width;
    this.precision = precision;
  }

  /**
   * Returns a possibly new FormatOptions instance possibly containing a subset of the formatting
   * information. This is useful if a backend implementation wishes to create formatting options
   * that ignore some of the specified formatting information.
   *
   * @param allowedFlags A mask of flag values to be retained in the returned instance. Use
   *     {@link #ALL_FLAGS} to retain all flag values, or {@code 0} to suppress all flags.
   * @param allowWidth specifies whether to include width in the returned instance.
   * @param allowPrecision specifies whether to include precision in the returned instance.
   */
  public FormatOptions filter(int allowedFlags, boolean allowWidth, boolean allowPrecision) {
    if (isDefault()) {
      return this;
    }
    int newFlags = allowedFlags & flags;
    int newWidth = allowWidth ? width : UNSET;
    int newPrecision = allowPrecision ? precision : UNSET;
    // Remember that we must never create a non-canonical default instance.
    if (newFlags == 0 && newWidth == UNSET && newPrecision == UNSET) {
      return DEFAULT;
    }
    // This check would be faster if we encoded the entire state into a long value. It's also
    // entirely possible we should just allocate a new instance and be damned (especially as
    // having anything other than the default instance is rare).
    // TODO(dbeaumont): Measure performance and see about removing this code, almost certainly fine.
    if (newFlags == flags && newWidth == width && newPrecision == precision) {
      return this;
    }
    return new FormatOptions(newFlags, newWidth, newPrecision);
  }

  /** Returns true if this instance has only default formatting options. */
  @SuppressWarnings("ReferenceEquality")
  public boolean isDefault() {
    return this == getDefault();
  }

  /**
   * Returns the width for these options, or {@link #UNSET} if not specified. This is a non-negative
   * decimal integer which typically indicates the minimum number of characters to be written to the
   * output, but its precise meaning is dependent on the formatting rule it is applied to.
   */
  public int getWidth() {
    return width;
  }

  /**
   * Returns the precision for these options, or {@link #UNSET} if not specified. This is a
   * non-negative decimal integer, usually used to restrict the number of characters, but its
   * precise meaning is dependent on the formatting rule it is applied to.
   */
  public int getPrecision() {
    return precision;
  }

  /**
   * Validates these options according to the allowed criteria and checks for inconsistencies in
   * flag values.
   * <p>
   * Note that there is not requirement for options used internally in custom message parsers to be
   * validated, but any format options passed through the {@code ParameterVisitor} interface must
   * be valid with respect to the associated {@link FormatChar} instance.
   *
   * @param allowedFlags a bit mask specifying a subset of the printf flags that are allowed for
   *        these options.
   * @param allowPrecision true if these options are allowed to have a precision value specified.
   * @return true if these options are valid given the specified constraints.
   */
  public boolean validate(int allowedFlags, boolean allowPrecision) {
    // The default instance is always valid (commonest case).
    if (isDefault()) {
      return true;
    }
    // Check if our flags are a subset of the allowed flags.
    if ((flags & ~allowedFlags) != 0) {
      return false;
    }
    // Check we only have precision specified when it is allowed.
    if (!allowPrecision && precision != UNSET) {
      return false;
    }
    return checkFlagConsistency(flags, getWidth() != UNSET);
  }

  // Helper to check for legal combinations of flags.
  static boolean checkFlagConsistency(int flags, boolean hasWidth) {
    // Check that we specify at most one of 'prefix plus' and 'prefix space'.
    if ((flags & (FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES | FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES))
        == (FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES | FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES)) {
      return false;
    }
    // Check that we specify at most one of 'left align' and 'leading zeros'.
    if ((flags & (FLAG_LEFT_ALIGN | FLAG_SHOW_LEADING_ZEROS))
        == (FLAG_LEFT_ALIGN | FLAG_SHOW_LEADING_ZEROS)) {
      return false;
    }
    // Check that if 'left align' or 'leading zeros' is specified, we also have a width value.
    if ((flags & (FLAG_LEFT_ALIGN | FLAG_SHOW_LEADING_ZEROS)) != 0 && !hasWidth) {
      return false;
    }
    return true;
  }

  /**
   * Validates these options as if they were being applied to the given {@link FormatChar} and
   * checks for inconsistencies in flag values.
   * <p>
   * Note that there is not requirement for options used internally in custom message parsers to be
   * validated, but any format options passed through the
   * {@link com.google.common.flogger.parameter.ParameterVisitor ParameterVisitor} interface must
   * be valid with respect to the associated {@link FormatChar} instance.
   *
   * @param formatChar the formatting rule to check these options against.
   * @return true if these options are valid for the given format.
   */
  public boolean areValidFor(FormatChar formatChar) {
    return validate(formatChar.getAllowedFlags(), formatChar.getType().supportsPrecision());
  }

  /**
   * Returns the flag bits for this options instance. Where possible the per-flag methods
   * {@code shouldXxx()} should be preferred for code clarity, but for efficiency and when testing
   * multiple flags values at the same time, this method is useful.
   */
  public int getFlags() {
    return flags;
  }

  /**
   * Corresponds to printf flag '-' (incompatible with '0').
   * <p>
   * Logging backends may ignore this flag, though it does provide some visual clarity when aligning
   * values.
   */
  public boolean shouldLeftAlign() {
    return (flags & FLAG_LEFT_ALIGN) != 0;
  }

  /**
   * Corresponds to printf flag '#'.
   * <p>
   * Logging backends should honor this flag for hex or octal, as it is a common way to avoid
   * ambiguity when formatting non-decimal values.
   */
  public boolean shouldShowAltForm() {
    return (flags & FLAG_SHOW_ALT_FORM) != 0;
  }

  /**
   * Corresponds to printf flag '0'.
   * <p>
   * Logging backends should honor this flag, as it is very commonly used to format hexadecimal or
   * octal values to allow specific bit values to be calculated.
   */
  public boolean shouldShowLeadingZeros() {
    return (flags & FLAG_SHOW_LEADING_ZEROS) != 0;
  }

  /**
   * Corresponds to printf flag '+'.
   * <p>
   * Logging backends are free to ignore this flag, though it does provide some visual clarity when
   * tabulating certain types of values.
   */
  public boolean shouldPrefixPlusForPositiveValues() {
    return (flags & FLAG_PREFIX_PLUS_FOR_POSITIVE_VALUES) != 0;
  }

  /**
   * Corresponds to printf flag ' '.
   * <p>
   * Logging backends are free to ignore this flag, though if they choose to support
   * {@link #shouldPrefixPlusForPositiveValues()} then it is advisable to support this as well.
   */
  public boolean shouldPrefixSpaceForPositiveValues() {
    return (flags & FLAG_PREFIX_SPACE_FOR_POSITIVE_VALUES) != 0;
  }

  /**
   * Corresponds to printf flag ','.
   * <p>
   * Logging backends are free to select the locale in which the formatting will occur or ignore
   * this flag altogether.
   */
  public boolean shouldShowGrouping() {
    return (flags & FLAG_SHOW_GROUPING) != 0;
  }

  /**
   * Corresponds to formatting with an upper-case format character.
   * <p>
   * Logging backends are free to ignore this flag.
   */
  public boolean shouldUpperCase() {
    return (flags & FLAG_UPPER_CASE) != 0;
  }

  /**
   * Appends the data for this options instance in a printf compatible form to the given buffer.
   * This method neither appends the leading {@code %} symbol nor a format type character. Output is
   * written in the form {@code [width][.precision][flags]} and for the default instance, nothing is
   * appended.
   *
   * @param out The output buffer to which the options are appended.
   */
  @CanIgnoreReturnValue
  public StringBuilder appendPrintfOptions(StringBuilder out) {
    if (!isDefault()) {
      // Knock out the upper-case flag because that does not correspond to an options character.
      int optionFlags = flags & ~FLAG_UPPER_CASE;
      for (int bit = 0; (1 << bit) <= optionFlags; bit++) {
        if ((optionFlags & (1 << bit)) != 0) {
          out.append(FLAG_CHARS_ORDERED.charAt(bit));
        }
      }
      if (width != UNSET) {
        out.append(width);
      }
      if (precision != UNSET) {
        out.append('.').append(precision);
      }
    }
    return out;
  }

  @Override
  public boolean equals(@NullableDecl Object o) {
    // Various functions ensure that the same instance gets re-used, so it seems likely that it's
    // worth optimizing for it here.
    if (o == this) {
      return true;
    }
    if (o instanceof FormatOptions) {
      FormatOptions other = (FormatOptions) o;
      return (other.flags == flags) && (other.width == width) && (other.precision == precision);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = flags;
    result = (31 * result) + width;
    result = (31 * result) + precision;
    return result;
  }
}
