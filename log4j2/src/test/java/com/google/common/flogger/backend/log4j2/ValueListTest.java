package com.google.common.flogger.backend.log4j2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public final class ValueListTest {

    @Test
    public void testValueList() {
        ValueList valueList = new ValueList(1, new ValueList(2, new ValueList(3, null)));
        assertThat(valueList.toString()).isEqualTo("[1, 2, 3]");
    }

    @Test
    public void assertSingleValue() {
        Object valueList = ValueList.maybeWrap(1, null);
        assertThat(valueList.toString()).isEqualTo("1");
    }

    @Test
    public void assertTwoValues_maybeWrap() {
        Object valueList1 = ValueList.maybeWrap(1, null);
        ValueList valueList2 = (ValueList) ValueList.maybeWrap(2, valueList1);
        assertThat(valueList2.toString()).isEqualTo("[2, 1]");
    }

    @Test
    public void assertThreeValues_maybeWrap() {
        Object valueList1 = ValueList.maybeWrap(1, null);
        ValueList valueList2 = (ValueList) ValueList.maybeWrap(2, valueList1);
        ValueList valueList3 = (ValueList) ValueList.maybeWrap(3, valueList2);
        assertThat(valueList3.toString()).isEqualTo("[3, 2, 1]");
    }

    @Test
    public void assertEmptyValueList() {
        ValueList valueList = new ValueList(null, null);
        assertThat(valueList.toString()).isEqualTo("null");
    }

    @Test
    public void assertValueListOfValueList() {
        ValueList valueList = new ValueList(new ValueList(1, new ValueList(2, null)), new ValueList(3, null));
        assertThat(valueList.toString()).isEqualTo("[[1, 2], 3]");
    }

    @Test
    public void assertListOfValueList() {
        ValueList valueList = new ValueList(Arrays.asList(1, 2, 3), null);
        assertThat(valueList.toString()).isEqualTo("[1, 2, 3]");
    }

    @Test
    public void assertNestedListOfValueList() {
        ValueList valueList = new ValueList(Arrays.asList(4, 5), new ValueList(Arrays.asList(1, 2, 3), null));
        assertThat(valueList.toString()).isEqualTo("[[4, 5], [1, 2, 3]]");
    }

    @Test
    public void assertValueListThrowsNPE() {
        try {
            ValueList.maybeWrap(null, null);
            assertThat(true).isFalse();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }
}