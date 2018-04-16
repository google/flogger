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

package com.google.common.flogger.parameter;

import com.google.common.flogger.backend.FormatChar;
import com.google.common.flogger.backend.FormatOptions;
import com.google.common.flogger.backend.FormatType;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * A parameter implementation to mimic the formatting of brace style placeholders (ie, "{n}").
 */
public class BraceStyleParameter extends Parameter {

  // Format options to mimic how '{0}' is formatted for numbers (i.e. like "%,d" or "%,f").
  private static final FormatOptions WITH_GROUPING =
      FormatOptions.of(FormatOptions.FLAG_SHOW_GROUPING, FormatOptions.UNSET, FormatOptions.UNSET);

  // Message formatter for fallback cases where '{n}' formats sufficiently differently to any
  // available printf specifier that we must preformat the result ourselves.
  // TODO: Get the Locale from the Platform class for better i18n support.
  private static final MessageFormat prototypeMessageFormatter =
      new MessageFormat("{0}", Locale.ROOT);

  /** Cache parameters with indices 0-9 to cover the vast majority of cases. */
  private static final int MAX_CACHED_PARAMETERS = 10;

  /** Map of the most common default general parameters (corresponds to %s, %d, %f etc...). */
  private static final BraceStyleParameter[] DEFAULT_PARAMETERS;

  static {
    DEFAULT_PARAMETERS = new BraceStyleParameter[MAX_CACHED_PARAMETERS];
    for (int index = 0; index < MAX_CACHED_PARAMETERS; index++) {
      DEFAULT_PARAMETERS[index] = new BraceStyleParameter(index);
    }
  }

  /**
   * Returns a {@link Parameter} representing a plain "brace style" placeholder "{n}". Note that a
   * cached value may be returned.
   *
   * @param index the index of the argument to be processed.
   * @return the immutable, thread safe parameter instance.
   */
  public static BraceStyleParameter of(int index) {
    return index < MAX_CACHED_PARAMETERS
        ? DEFAULT_PARAMETERS[index]
        : new BraceStyleParameter(index);
  }

  private BraceStyleParameter(int index) {
    super(FormatOptions.getDefault(), index);
  }

  @Override
  protected void accept(ParameterVisitor visitor, Object value) {
    // Special cases which MessageFormat treats specially (oddly Calendar is not a special case).
    if (FormatType.INTEGRAL.canFormat(value)) {
      visitor.visit(value, FormatChar.DECIMAL, WITH_GROUPING);
    } else if (FormatType.FLOAT.canFormat(value)) {
      // Technically floating point formatting via {0} differs from "%,f", but as "%,f" results in
      // more precision it seems better to mimic "%,f" rather than discard both precision and type
      // information by calling visitPreformatted().
      visitor.visit(value, FormatChar.FLOAT, WITH_GROUPING);
    } else if (value instanceof Date) {
      // MessageFormat is not thread safe, so we always clone().
      String formatted = ((MessageFormat) prototypeMessageFormatter.clone())
          .format(new Object[] {value}, new StringBuffer(), null /* field position */)
          .toString();
      visitor.visitPreformatted(value, formatted);
    } else if (value instanceof Calendar) {
      visitor.visitDateTime(value, DateTimeFormat.DATETIME_FULL, getFormatOptions());
    } else {
      visitor.visit(value, FormatChar.STRING, getFormatOptions());
    }
  }

  @Override
  public String getFormat() {
    return "%s";
  }
}
