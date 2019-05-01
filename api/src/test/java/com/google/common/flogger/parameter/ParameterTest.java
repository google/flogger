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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.flogger.backend.FormatOptions;
import com.google.common.flogger.parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ParameterTest {
  // We just need a concrete class here.
  private static class TestParameter extends Parameter {
    public TestParameter(FormatOptions options, int index) {
      super(options, index);
    }

    @Override
    protected void accept(ParameterVisitor visitor, Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getFormat() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testBadArgs() {
    FormatOptions options = FormatOptions.getDefault();
    try {
      new TestParameter(null, 0);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
    try {
      new TestParameter(options, -1);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testGetOptions() throws ParseException {
    FormatOptions options = FormatOptions.parse("-2.2", 0, 4, false);
    Parameter p = new TestParameter(options, 0);
    assertThat(p.getFormatOptions()).isSameInstanceAs(options);
  }

  @Test
  public void testSingleArgumentParameter() {
    Parameter p0 = new TestParameter(FormatOptions.getDefault(), 0);
    Parameter p1 = new TestParameter(FormatOptions.getDefault(), 1);

    assertThat(p0.getIndex()).isEqualTo(0);
    assertThat(p1.getIndex()).isEqualTo(1);
  }
}
