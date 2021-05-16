package com.google.common.flogger.backend.log4j2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

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
        assertThat(ValueQueue.appendValueToNewQueue("").toString()).isEqualTo("");
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
        ValueQueue valueQueue = ValueQueue.appendValueToNewQueue(Arrays.asList(Arrays.asList(4, 5), Arrays.asList(1, 2, 3)));
        assertThat(valueQueue.toString()).isEqualTo("[[4, 5], [1, 2, 3]]");
    }

    @Test
    public void assertValueListThrowsNPE() {
        try {
            ValueQueue.maybeWrap(null, null);
            assertThat(true).isFalse();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }
}