/*
 * Copyright (C) 2015 The Flogger Authors.
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

import static com.google.common.flogger.backend.FormatType.BOOLEAN;
import static com.google.common.flogger.backend.FormatType.CHARACTER;
import static com.google.common.flogger.backend.FormatType.FLOAT;
import static com.google.common.flogger.backend.FormatType.GENERAL;
import static com.google.common.flogger.backend.FormatType.INTEGRAL;
import static com.google.common.flogger.testing.FormatTypeSubject.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FormatTypeTest {
  private static final Object ANY_OBJECT = new Object();

  @Test
  public void testGeneralFormatType() {
    assertThat(GENERAL).canFormat(ANY_OBJECT);
    assertThat(GENERAL).canFormat("any string");
    assertThat(GENERAL).isNotNumeric();
  }

  @Test
  public void testBooleanFormatType() {
    assertThat(BOOLEAN).canFormat(Boolean.TRUE);
    assertThat(BOOLEAN).canFormat(false);

    assertThat(BOOLEAN).cannotFormat(ANY_OBJECT);
    assertThat(BOOLEAN).cannotFormat("any string");
    assertThat(BOOLEAN).isNotNumeric();
  }

  @Test
  public void testCharacterFormatType() {
    assertThat(CHARACTER).canFormat('a');
    assertThat(CHARACTER).canFormat((byte) 0);
    assertThat(CHARACTER).canFormat(0);
    assertThat(CHARACTER).canFormat(Character.MAX_CODE_POINT);

    assertThat(CHARACTER).cannotFormat(0L);
    assertThat(CHARACTER).cannotFormat(BigInteger.ZERO);
    assertThat(CHARACTER).cannotFormat(Character.MAX_CODE_POINT + 1);
    assertThat(CHARACTER).cannotFormat(false);
    assertThat(CHARACTER).cannotFormat(ANY_OBJECT);
    assertThat(CHARACTER).cannotFormat("any string");
    assertThat(CHARACTER).isNotNumeric();
  }

  @Test
  public void testIntegralFormatType() {
    assertThat(INTEGRAL).canFormat(10);
    assertThat(INTEGRAL).canFormat(10L);
    assertThat(INTEGRAL).canFormat(BigInteger.TEN);
    assertThat(INTEGRAL).isNumeric();

    assertThat(INTEGRAL).cannotFormat(10.0);
    assertThat(INTEGRAL).cannotFormat(10.0D);
    assertThat(INTEGRAL).cannotFormat(BigDecimal.TEN);
    assertThat(INTEGRAL).cannotFormat('a');
    assertThat(INTEGRAL).cannotFormat(false);
    assertThat(INTEGRAL).cannotFormat(ANY_OBJECT);
    assertThat(INTEGRAL).cannotFormat("any string");
  }

  @Test
  public void testFloatFormatType() {
    assertThat(FLOAT).canFormat(10.0);
    assertThat(FLOAT).canFormat(10.0D);
    assertThat(FLOAT).canFormat(BigDecimal.TEN);
    assertThat(FLOAT).isNumeric();

    assertThat(FLOAT).cannotFormat(10);
    assertThat(FLOAT).cannotFormat(10L);
    assertThat(FLOAT).cannotFormat(BigInteger.TEN);
    assertThat(FLOAT).cannotFormat('a');
    assertThat(FLOAT).cannotFormat(false);
    assertThat(FLOAT).cannotFormat(ANY_OBJECT);
    assertThat(FLOAT).cannotFormat("any string");
  }
}
