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

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The general formatting type of any one of the predefined {@code FormatChar} instances.
 */
public enum FormatType {
  /** General formatting that can be applied to any type. */
  GENERAL(false, true) {
    @Override
    public boolean canFormat(Object arg) {
      return true;
    }
  },

  /** Formatting that can be applied to any boolean type. */
  BOOLEAN(false, false) {
    @Override
    public boolean canFormat(Object arg) {
      return arg instanceof Boolean;
    }
  },

  /**
   * Formatting that can be applied to Character or any integral type that can be losslessly
   * converted to an int and for which {@link Character#isValidCodePoint(int)} returns true.
   */
  CHARACTER(false, false) {
    @Override
    public boolean canFormat(Object arg) {
      // Ordering in relative likelihood.
      if (arg instanceof Character) {
        return true;
      } else if ((arg instanceof Integer) || (arg instanceof Byte) || (arg instanceof Short)) {
        return Character.isValidCodePoint(((Number) arg).intValue());
      } else {
        return false;
      }
    }
  },

  /**
   * Formatting that can be applied to any integral Number type. Logging backends must support Byte,
   * Short, Integer, Long and BigInteger but may also support additional numeric types directly. A
   * logging backend that encounters an unknown numeric type should fall back to using
   * {@code toString()}.
   */
  INTEGRAL(true, false) {
    @Override
    public boolean canFormat(Object arg) {
      // Ordering in relative likelihood.
      return (arg instanceof Integer)
          || (arg instanceof Long)
          || (arg instanceof Byte)
          || (arg instanceof Short)
          || (arg instanceof BigInteger);
    }
  },

  /**
   * Formatting that can be applied to any Number type. Logging backends must support all the
   * integral types as well as Float, Double and BigDecimal, but may also support additional numeric
   * types directly. A logging backend that encounters an unknown numeric type should fall back to
   * using {@code toString()}.
   */
  FLOAT(true, true) {
    @Override
    public boolean canFormat(Object arg) {
      // Ordering in relative likelihood.
      return (arg instanceof Double) || (arg instanceof Float) || (arg instanceof BigDecimal);
    }
  };

  private final boolean isNumeric;
  private final boolean supportsPrecision;

  private FormatType(boolean isNumeric, boolean supportsPrecision) {
    this.isNumeric = isNumeric;
    this.supportsPrecision = supportsPrecision;
  }

  /**
   * True if the notion of a specified precision value makes sense to this format type. Precision is
   * specified in addition to width and can control the resolution of a formatting operation (e.g.
   * how many digits to output after the decimal point for floating point values).
   */
  boolean supportsPrecision() {
    return supportsPrecision;
  }

  /**
   * True if this format type requires a {@link Number} instance (or one of the corresponding
   * fundamental types) as an argument.
   */
  public boolean isNumeric() {
    return isNumeric;
  }

  public abstract boolean canFormat(Object arg);
}
