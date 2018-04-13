/*
 * Copyright (C) 2014 The Flogger Authors.
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

import com.google.common.flogger.backend.FormatOptions;

/**
 * A parameter for formatting date/time arguments.
 * <p>
 * This class is immutable and thread safe, as per the Parameter contract.
 */
public final class DateTimeParameter extends Parameter {
  /**
   * Returns a {@link Parameter} representing the given formatting options of the specified
   * date/time formatting character. Note that a cached value may be returned.
   *
   * @param format specifier for the specific date/time formatting to be applied.
   * @param options the validated formatting options.
   * @param index the argument index.
   * @return the immutable, thread safe parameter instance.
   */
  public static Parameter of(DateTimeFormat format, FormatOptions options, int index) {
    return new DateTimeParameter(options, index, format);
  }

  private final DateTimeFormat format;
  private final String formatString;

  private DateTimeParameter(FormatOptions options, int index, DateTimeFormat format) {
    super(options, index);
    this.format = format;
    this.formatString =
        options
            .appendPrintfOptions(new StringBuilder("%"))
            .append(options.shouldUpperCase() ? 'T' : 't')
            .append(format.getChar())
            .toString();
  }

  @Override
  protected void accept(ParameterVisitor visitor, Object value) {
    visitor.visitDateTime(value, format, getFormatOptions());
  }

  @Override
  public String getFormat() {
    return formatString;
  }
}
