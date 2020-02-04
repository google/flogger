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
public class DefaultPrintfMessageParserTest {
  private static final PrintfMessageParser PARSER = DefaultPrintfMessageParser.getInstance();

  @Test
  public void testParsePrintf() throws ParseException {
    MessageBuilder<?> builder = mock(MessageBuilder.class);
    int unused = PARSER.parsePrintfTerm(builder, 1, "Hello %2$+06.2f World", 6, 9, 14);

    // Capture the parameter created by the parsing of the printf term.
    ArgumentCaptor<Parameter> param = ArgumentCaptor.forClass(Parameter.class);
    verify(builder).addParameterImpl(eq(6), eq(15), param.capture());
    assertThat(param.getValue().getIndex()).isEqualTo(1);

    // Now visit the parameter and capture its state (doing it this way avoids needing to open up
    // methods on the Parameter interface just for testing).
    ParameterVisitor out = mock(ParameterVisitor.class);
    param.getValue().accept(out, new Object[] {"Answer: ", 42.0});

    // Recover the captured arguments and check that the right formatting was done.
    ArgumentCaptor<FormatOptions> options = ArgumentCaptor.forClass(FormatOptions.class);
    verify(out).visit(eq(42.0), eq(FormatChar.FLOAT), options.capture());
    assertThat(options.getValue().getWidth()).isEqualTo(6);
    assertThat(options.getValue().getPrecision()).isEqualTo(2);
    assertThat(options.getValue().shouldShowLeadingZeros()).isTrue();
    assertThat(options.getValue().shouldPrefixPlusForPositiveValues()).isTrue();
  }

  @Test
  public void testUnknownPrintfFormat() {
    try {
      int unused = PARSER.parsePrintfTerm(null, 0, "%Q", 0, 1, 1);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("[%Q]");
    }
  }

  @Test
  public void testInvalidPrintfFlags() {
    try {
      int unused = PARSER.parsePrintfTerm(null, 0, "%0s", 0, 1, 2);
      fail("expected ParseException");
    } catch (ParseException expected) {
      assertThat(expected.getMessage()).contains("[%0s]");
    }
  }
}
