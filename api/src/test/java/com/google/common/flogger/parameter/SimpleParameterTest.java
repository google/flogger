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

import static com.google.common.flogger.backend.FormatChar.DECIMAL;
import static com.google.common.flogger.backend.FormatChar.FLOAT;
import static com.google.common.flogger.backend.FormatChar.HEX;
import static com.google.common.flogger.backend.FormatChar.STRING;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.backend.FormatChar;
import com.google.common.flogger.backend.FormatOptions;
import com.google.common.flogger.parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleParameterTest {
  @Test
  public void testCaching() throws ParseException {
    // The same instance is used for all types without options up to (at least) 10 indices.
    for (FormatChar c : FormatChar.values()) {
      for (int n = 0; n < 10; n++) {
        assertThat(SimpleParameter.of(n, c, FormatOptions.getDefault()))
            .isSameInstanceAs(SimpleParameter.of(n, c, FormatOptions.getDefault()));
      }
    }
    // Different indices do not return the same instances.
    assertThat(SimpleParameter.of(1, FormatChar.DECIMAL, FormatOptions.getDefault()))
        .isNotSameInstanceAs(SimpleParameter.of(0, FormatChar.DECIMAL, FormatOptions.getDefault()));
    // Different format chars do not return the same instances.
    assertThat(SimpleParameter.of(0, FormatChar.FLOAT, FormatOptions.getDefault()))
        .isNotSameInstanceAs(SimpleParameter.of(0, FormatChar.DECIMAL, FormatOptions.getDefault()));
    // Different formatting options do not return the same instances.
    assertThat(SimpleParameter.of(0, FormatChar.DECIMAL, FormatOptions.parse("-10", 0, 3, false)))
        .isNotSameInstanceAs(SimpleParameter.of(0, FormatChar.DECIMAL, FormatOptions.getDefault()));
  }

  @Test
  public void testPrintfFormatString() {
    assertThat(SimpleParameter.buildFormatString(parseOptions("-20", false), STRING))
        .isEqualTo("%-20s");
    assertThat(SimpleParameter.buildFormatString(parseOptions("0#16", true), HEX))
        .isEqualTo("%#016X");
    // Printing can reorder the flags ...
    assertThat(SimpleParameter.buildFormatString(parseOptions("+-20", false), DECIMAL))
        .isEqualTo("%+-20d");
    assertThat(SimpleParameter.buildFormatString(parseOptions(",020.10", false), FLOAT))
        .isEqualTo("%,020.10f");
  }

  private static FormatOptions parseOptions(String s, boolean isUpperCase) {
    try {
      return FormatOptions.parse(s, 0, s.length(), isUpperCase);
    } catch (ParseException parseException) {
      throw new RuntimeException(parseException);
    }
  }
}
