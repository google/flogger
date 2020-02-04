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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.flogger.backend.FormatChar;
import com.google.common.flogger.backend.FormatOptions;
import com.google.common.flogger.parameter.Parameter;
import com.google.common.flogger.parameter.ParameterVisitor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

@RunWith(JUnit4.class)
public class DefaultBraceStyleMessageParserTest {
  private static final BraceStyleMessageParser PARSER =
      DefaultBraceStyleMessageParser.getInstance();
  private static final FormatOptions WITH_GROUPING =
      FormatOptions.of(FormatOptions.FLAG_SHOW_GROUPING, FormatOptions.UNSET, FormatOptions.UNSET);

  @Test
  public void testParseBraceFormat() throws ParseException {
    MessageBuilder<?> builder = mock(MessageBuilder.class);
    // Parse just the 3 characters representing the brace format specifier between position 6 and 9.
    // The -1 indicates that there's no additional formatting information after the index.
    PARSER.parseBraceFormatTerm(builder, 1, "Hello {1} World", 6, -1, 9);

    // Capture the parameter created by the parsing of the printf term.
    ArgumentCaptor<Parameter> param = ArgumentCaptor.forClass(Parameter.class);
    verify(builder).addParameterImpl(eq(6), eq(9), param.capture());
    assertThat(param.getValue().getIndex()).isEqualTo(1);

    // Now visit the parameter and verify the expected callback occurred (doing it this way avoids
    // needing to open up methods on the Parameter interface just for testing).
    ParameterVisitor out = mock(ParameterVisitor.class);
    param.getValue().accept(out, new Object[] {"Answer: ", 42});
    // Check the expected values were passed (decimals should be formatted like "%,d").
    verify(out).visit(eq(42), eq(FormatChar.DECIMAL), eq(WITH_GROUPING));
  }

  @Test
  public void testTrailingFormatNotSupportedInBraceFormat() {
    try {
      PARSER.parseBraceFormatTerm(null, 0, "{0:x}", 0, 3, 5);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("[:x]");
    }
  }
}
