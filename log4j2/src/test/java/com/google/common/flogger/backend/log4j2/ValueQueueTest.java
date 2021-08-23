/*
 * Copyright (C) 2021 The Flogger Authors.
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

package com.google.common.flogger.backend.log4j2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ValueQueue}. */
@RunWith(JUnit4.class)
public class ValueQueueTest {

  @Test
  public void testValueQueue() {
    ValueQueue valueQueue = ValueQueue.appendValueToNewQueue(Arrays.asList(1, 2, 3));
    assertThat(valueQueue.toString()).isEqualTo("[1, 2, 3]");
  }

  @Test
  public void assertSingleValue() {
    Object valueList = ValueQueue.maybeWrap(1, null);
    assertThat(valueList.toString()).isEqualTo("1");
  }

  @Test
  public void assertTwoValues_maybeWrap() {
    Object valueQueue1 = ValueQueue.maybeWrap(1, null);
    ValueQueue valueQueue = (ValueQueue) ValueQueue.maybeWrap(2, valueQueue1);
    assertThat(valueQueue.toString()).isEqualTo("[1, 2]");
  }

  @Test
  public void assertThreeValues_maybeWrap() {
    Object valueQueue1 = ValueQueue.maybeWrap(1, null);
    ValueQueue valueQueue2 = (ValueQueue) ValueQueue.maybeWrap(2, valueQueue1);
    ValueQueue valueQueue3 = (ValueQueue) ValueQueue.maybeWrap(3, valueQueue2);
    assertThat(valueQueue3.toString()).isEqualTo("[1, 2, 3]");
  }

  @Test
  public void assertEmptyValueQueue() {
    assertThat(ValueQueue.appendValueToNewQueue("").toString()).isEmpty();
  }

  @Test
  public void assertValueQueueOfValueQueue() {
    ValueQueue valueQueue = ValueQueue.appendValueToNewQueue(Arrays.asList(Arrays.asList(1, 2), 3));
    assertThat(valueQueue.toString()).isEqualTo("[[1, 2], 3]");
  }

  @Test
  public void assertListOfValueQueue() {
    ValueQueue valueQueue = ValueQueue.appendValueToNewQueue(Arrays.asList(1, 2, 3));
    assertThat(valueQueue.toString()).isEqualTo("[1, 2, 3]");
  }

  @Test
  public void assertNestedListOfValueQueue() {
    ValueQueue valueQueue =
        ValueQueue.appendValueToNewQueue(
            Arrays.asList(Arrays.asList(4, 5), Arrays.asList(1, 2, 3)));
    assertThat(valueQueue.toString()).isEqualTo("[[4, 5], [1, 2, 3]]");
  }

  @Test
  public void assertValueListThrowsNpe() {
    try {
      ValueQueue.maybeWrap(null, null);
      fail();
    } catch (NullPointerException expected) {
    }
  }
}
