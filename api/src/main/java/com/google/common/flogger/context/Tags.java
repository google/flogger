/*
 * Copyright (C) 2016 The Flogger Authors.
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

package com.google.common.flogger.context;

import static com.google.common.flogger.util.Checks.checkMetadataIdentifier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Immutable tags which can be attached to log statements via platform specific injection
 * mechanisms.
 *
 * <p>A tag is either a "simple" tag, added via {@link Builder#addTag(String)} or a tag with a
 * value, added via one of the {@code addTag(name,value)} methods. When thinking of tags as a {@code
 * Map<String, Set<Object>>}, the value of a "simple" tag is the empty set.
 *
 * <p>Tag values can be of several simple types and are held in a stable, sorted order within a
 * {@code Tags} instance. In other words it never matters in which order two {@code Tags} instances
 * are merged.
 *
 * <p>When tags are merged, the result is the union of the values. This is easier to explain When
 * thinking of tags as a {@code Map<String, Set<Object>>}, where "merging" means taking the union of
 * the {@code Set} associated with the tag name. In particular, for a given tag name:
 *
 * <ul>
 *   <li>Adding the same value for a given tag twice has no additional effect.
 *   <li>Adding a simple tag twice has no additional effect.
 *   <li>Adding a tag with a value is also implicitly like adding a simple tag with the same name.
 * </ul>
 *
 * <p>The {@link #toString} implementation of this class provides a human readable, machine parsable
 * representation of the tags.
 */
public final class Tags {
  /**
   * Allowed types of tag values. This ensures that tag values have well known semantics and can
   * always be formatted in a clearly and unambiguously.
   */
  // The ordering of elements in this enum should not change as it defines the sort order between
  // values of different types. New elements need not be added at the end though.
  private enum Type {
    BOOLEAN() {
      @Override
      int compare(Object lhs, Object rhs) {
        return ((Boolean) lhs).compareTo((Boolean) rhs);
      }
    },
    STRING() {
      @Override
      int compare(Object lhs, Object rhs) {
        return ((String) lhs).compareTo((String) rhs);
      }
    },
    LONG() {
      @Override
      int compare(Object lhs, Object rhs) {
        return ((Long) lhs).compareTo((Long) rhs);
      }
    },
    DOUBLE() {
      @Override
      int compare(Object lhs, Object rhs) {
        return ((Double) lhs).compareTo((Double) rhs);
      }
    };

    abstract int compare(Object lhs, Object rhs);

    private static Type of(Object tag) {
      // There should be exactly as many public methods to set tag values as there are cases here.
      if (tag instanceof String) {
        return STRING;
      } else if (tag instanceof Boolean) {
        return BOOLEAN;
      } else if (tag instanceof Long) {
        return LONG;
      } else if (tag instanceof Double) {
        return DOUBLE;
      } else {
        // Should never happen because only known types can be passed via public methods.
        throw new AssertionError("invalid tag type: " + tag.getClass());
      }
    }
  }

  private static final Comparator<Object> VALUE_COMPARATOR =
      new Comparator<Object>() {
        @Override
        public int compare(Object lhs, Object rhs) {
          // By API we only get known types here, all of which are final and comparable.
          Type ltype = Type.of(lhs);
          Type rtype = Type.of(rhs);
          return (ltype == rtype) ? ltype.compare(lhs, rhs) : ltype.compareTo(rtype);
        }
      };

  private static final SortedSet<Object> EMPTY_SET =
      Collections.unmodifiableSortedSet(new TreeSet<Object>());

  private static final Tags EMPTY_TAGS = new Tags(new TreeMap<String, SortedSet<Object>>());

  /** A mutable builder for tags. */
  public static final class Builder {
    private final SortedMap<String, SortedSet<Object>> map =
        new TreeMap<String, SortedSet<Object>>();

    /**
     * Adds an empty tag, ensuring that the given name exists in the tag map with at least an empty
     * set of values. Adding the same name more than once has no effect.
     *
     * <p>When viewed as a {@code Set}, the value for an empty tag is just the empty set. However if
     * other values are added for the same name, the set of values will no longer be empty and the
     * call to {@link #addTag(String)} will have had no lasting effect.
     */
    @CanIgnoreReturnValue
    public Builder addTag(String name) {
      Set<Object> values = map.get(checkMetadataIdentifier(name));
      if (values == null) {
        map.put(name, EMPTY_SET);
      }
      return this;
    }

    /**
     * Adds a string value for the given name, ensuring that the values for the given name contain
     * at least this value. Adding the same name/value pair more than once has no effect.
     */
    @CanIgnoreReturnValue
    public Builder addTag(String name, String value) {
      if (value == null) {
        throw new IllegalArgumentException("tag values cannot be null");
      }
      addImpl(name, value);
      return this;
    }

    /**
     * Adds a boolean value for the given name, ensuring that the values for the given name contain
     * at least this value. Adding the same name/value pair more than once has no effect.
     */
    @CanIgnoreReturnValue
    public Builder addTag(String name, boolean value) {
      addImpl(name, value);
      return this;
    }

    /**
     * Adds a long value for the given name, ensuring that the values for the given name contain at
     * least this value. Adding the same name/value pair more than once has no effect.
     *
     * <p>Note however that for numeric values, differing types (long/double) are always considered
     * distinct, so invoking both {@code addTag("foo", 1234L)} and {@code addTag("foo", 1234.0D)}
     * will result in two values for the tag.
     */
    @CanIgnoreReturnValue
    public Builder addTag(String name, long value) {
      addImpl(name, value);
      return this;
    }

    /**
     * Adds a double value for the given name, ensuring that the values for the given name contain
     * at least this value. Adding the same name/value pair more than once has no effect.
     *
     * <p>Note however that for numeric values, differing types (long/double) are always considered
     * distinct, so invoking both {@code addTag("foo", 1234L)} and {@code addTag("foo", 1234.0D)}
     * will result in two values for the tag.
     */
    @CanIgnoreReturnValue
    public Builder addTag(String name, double value) {
      addImpl(name, value);
      return this;
    }

    private void addImpl(String name, Object value) {
      // Checks and auto-boxing ensure that "value" is never null.
      SortedSet<Object> values = map.get(checkMetadataIdentifier(name));
      if (values == null || values == EMPTY_SET) {
        values = new TreeSet<Object>(VALUE_COMPARATOR);
        map.put(name, values);
      }
      values.add(value);
    }

    /** Returns an immutable tags instance. */
    public Tags build() {
      if (map.isEmpty()) {
        return EMPTY_TAGS;
      }
      SortedMap<String, SortedSet<Object>> copy = new TreeMap<String, SortedSet<Object>>();
      for (Map.Entry<String, SortedSet<Object>> e : map.entrySet()) {
        copy.put(e.getKey(), Collections.unmodifiableSortedSet(new TreeSet<Object>(e.getValue())));
      }
      return new Tags(copy);
    }

    @Override
    public String toString() {
      return build().toString();
    }
  }

  /** Returns a new builder for adding tags. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns the immutable empty tags instance. */
  public static Tags empty() {
    return EMPTY_TAGS;
  }

  /**
   * Returns a single tag without needing to use the builder API. Where multiple tags are needed, it
   * is always better to use the builder directly.
   */
  public static Tags of(String name, String value) {
    return builder().addTag(name, value).build();
  }

  /**
   * Returns a single tag without needing to use the builder API. Where multiple tags are needed, it
   * is always better to use the builder directly.
   */
  public static Tags of(String name, boolean value) {
    return builder().addTag(name, value).build();
  }

  /**
   * Returns a single tag without needing to use the builder API. Where multiple tags are needed, it
   * is always better to use the builder directly.
   */
  public static Tags of(String name, long value) {
    return builder().addTag(name, value).build();
  }

  /**
   * Returns a single tag without needing to use the builder API. Where multiple tags are needed, it
   * is always better to use the builder directly.
   */
  public static Tags of(String name, double value) {
    return builder().addTag(name, value).build();
  }

  private final SortedMap<String, SortedSet<Object>> map;
  private Integer hashCode = null;
  private String toString = null;

  // Callers are not expected to retain the map after this instance is created, so it doesn't
  // need to be copied, just wrapped.
  private Tags(SortedMap<String, SortedSet<Object>> map) {
    this.map = Collections.unmodifiableSortedMap(map);
  }

  /** Returns an immutable map containing the tag values. */
  public SortedMap<String, SortedSet<Object>> asMap() {
    return map;
  }

  /** Returns whether this instance is empty. */
  public boolean isEmpty() {
    // In theory only the EMPTY_TAGS instance will ever be empty, but this check is not expensive.
    return map.isEmpty();
  }

  /** Merges two tags instances, combining values for any name contained in both. */
  public Tags merge(Tags other) {
    // Dereference "other" first as a null pointer check so we cannot risk returning null later.
    if (other.isEmpty()) {
      return this;
    }
    if (this.isEmpty()) {
      return other;
    }
    SortedMap<String, SortedSet<Object>> merged = new TreeMap<String, SortedSet<Object>>();
    for (Map.Entry<String, SortedSet<Object>> e : map.entrySet()) {
      SortedSet<Object> otherValues = other.map.get(e.getKey());
      if (otherValues == null || e.getValue().containsAll(otherValues)) {
        // Our values are a superset of the values in the other map, so just use them.
        merged.put(e.getKey(), e.getValue());
      } else if (otherValues.containsAll(e.getValue())) {
        // The other map's values are a superset of ours, so just use them.
        merged.put(e.getKey(), otherValues);
      } else {
        // Values exist in both maps and neither is a superset, so we really must merge.
        SortedSet<Object> mergedValues = new TreeSet<Object>(e.getValue());
        mergedValues.addAll(otherValues);
        merged.put(e.getKey(), Collections.unmodifiableSortedSet(mergedValues));
      }
    }
    for (Map.Entry<String, SortedSet<Object>> e : other.map.entrySet()) {
      // Finally add those values that were only in the other map.
      if (!map.containsKey(e.getKey())) {
        merged.put(e.getKey(), e.getValue());
      }
    }
    return new Tags(merged);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    return (obj instanceof Tags) && ((Tags) obj).map.equals(map);
  }

  @Override
  public int hashCode() {
    if (hashCode == null) {
      // Unmodifiable maps cannot cache hash codes (the underlying map might be mutated), so to
      // avoid repeating potentially expensive hashcode calculations, we cache it. We could also
      // do this during construction, but the hashcode of a Tags instance won't be needed often.
      hashCode = map.hashCode();
    }
    return hashCode;
  }

  /**
   * Returns human readable representation of the tags. This is not a stable representation and may
   * change over time. If you need to format tags reliably for logging, you should not rely on this
   * method.
   */
  @Override
  public String toString() {
    if (toString == null) {
      toString = map.toString();
    }
    return toString;
  }
}
