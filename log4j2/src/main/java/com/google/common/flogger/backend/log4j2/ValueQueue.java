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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.context.Tags;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A simple FIFO queue linked-list implementation designed to store multiple metadata values in a
 * StringMap. There are two aspects worth pointing out:
 *
 * <p>First, it is expected that a value queue always contains at least a single item. You cannot
 * add null references to the queue and you cannot create an empty queue.
 *
 * <p>Second, it is expected to access the contents of the value queue via an iterator only. Hence
 * we do not provide a method for taking the first item in the value queue..
 *
 * <p>Metadata values in Flogger always have unique keys, but those keys can have the same label.
 * Because Log4j2 uses a {@code String} keyed map, we would risk clashing of values if we just used
 * the label to store each value directly. This class lets us store a list of values for a single
 * label while being memory efficient in the common case where each label really does only have one
 * value.
 */
final class ValueQueue implements Iterable<Object> {
  // Since the number of elements is almost never above 1 or 2, a LinkedList saves space.
  private final List<Object> values = new LinkedList<>();

  private ValueQueue() {}

  static ValueQueue newQueue(Object item) {
    checkNotNull(item, "item");
    ValueQueue valueQueue = new ValueQueue();
    valueQueue.put(item);
    return valueQueue;
  }

  static Object maybeWrap(Object value, @NullableDecl Object existingValue) {
    checkNotNull(value, "value");
    if (existingValue == null) {
      return value;
    } else {
      // This should only rarely happen, so a few small allocations seems acceptable.
      ValueQueue existingQueue =
          existingValue instanceof ValueQueue
              ? (ValueQueue) existingValue
              : ValueQueue.newQueue(existingValue);
      existingQueue.put(value);
      return existingQueue;
    }
  }

  static void appendValues(String label, Object valueOrQueue, MetadataKey.KeyValueHandler kvh) {
    if (valueOrQueue instanceof ValueQueue) {
      for (Object value : (ValueQueue) valueOrQueue) {
        emit(label, value, kvh);
      }
    } else {
      emit(label, valueOrQueue, kvh);
    }
  }

  /**
   * Helper method for creating and initializing a value queue with a non-nullable value. If value
   * is an instance of Tags, each tag will be added to the value queue.
   */
  static ValueQueue appendValueToNewQueue(Object value) {
    ValueQueue valueQueue = new ValueQueue();
    ValueQueue.emit(null, value, (k, v) -> valueQueue.put(v));
    return valueQueue;
  }

  /**
   * Emits a metadata label/value pair to a given {@code KeyValueHandler}, handling {@code Tags}
   * values specially.
   *
   * <p>Tags are key-value mappings which cannot be modified or replaced. If you add the tag mapping
   * {@code "foo" -> true} and later add {@code "foo" -> false}, you get "foo" mapped to both true
   * and false. This is very deliberate since the key space for tags is global and the risk of two
   * bits of code accidentally using the same tag name is real (e.g. you add "id=xyz" to a scope,
   * but you see "id=abcd" because someone else added "id=abcd" in a context you weren't aware of).
   *
   * <p>Given three tag mappings:
   * <ul>
   *   <li>{@code "baz"} (no value)
   *   <li>{@code "foo" -> true}
   *   <li>{@code "foo" -> false}
   * </ul>
   *
   * the value queue is going to store the mappings as:
   * <pre>{@code
   * tags=[baz, foo=false, foo=true]
   * }</pre>
   *
   * <p>Reusing the label 'tags' is intentional as this allows us to store the flatten tags in
   * Log4j2's ContextMap.
   */
  static void emit(String label, Object value, MetadataKey.KeyValueHandler kvh) {
    if (value instanceof Tags) {
      // Flatten tags to treat them as keys or key/value pairs, e.g. tags=[baz=bar, baz=bar2, foo]
      ((Tags) value)
          .asMap()
          .forEach(
              (k, v) -> {
                if (v.isEmpty()) {
                  kvh.handle(label, k);
                } else {
                  for (Object obj : v) {
                    kvh.handle(label, k + "=" + obj);
                  }
                }
              });
    } else {
      kvh.handle(label, value);
    }
  }

  @Override
  public Iterator<Object> iterator() {
    return values.iterator();
  }

  void put(Object item) {
    checkNotNull(item, "item");
    values.add(item);
  }

  int size() {
    return values.size();
  }

  /**
   * Returns a string representation of the contents of the specified value queue.
   *
   * <ul>
   *   <li>If the value queue is empty, the method returns an empty string.
   *   <li>If the value queue contains a single element {@code a}, this method returns {@code
   *       a.toString()}.
   *   <li>Otherwise, the contents of the queue are formatted like a {@code List}.
   * </ul>
   */
  @Override
  public String toString() {
    // This case shouldn't actually happen unless you use the value queue for storing emitted values
    if (values.isEmpty()) {
      return "";
    }
    // Consider using MessageUtils.safeToString() here.
    if (values.size() == 1) {
      return values.get(0).toString();
    }
    return values.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ValueQueue that = (ValueQueue) o;
    return values.equals(that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(values);
  }
}
