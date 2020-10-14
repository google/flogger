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
import static com.google.common.flogger.util.Checks.checkNotNull;
import static com.google.common.flogger.util.Checks.checkState;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

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

  private static final Tags EMPTY_TAGS = new Tags(Collections.<String, Set<Object>>emptyMap());

  /** A mutable builder for tags. */
  public static final class Builder {
    private final Map<String, Set<Object>> map = new TreeMap<String, Set<Object>>();

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
        map.put(name, Collections.emptySet());
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
      Set<Object> values = map.get(checkMetadataIdentifier(name));
      // If a tag without a value is added, the set is empty *and* unmodifiable.
      if (values == null || values.isEmpty()) {
        values = new TreeSet<Object>(VALUE_COMPARATOR);
        map.put(name, values);
      }
      values.add(value);
    }

    /** Returns an immutable tags instance. */
    public Tags build() {
      return map.isEmpty() ? EMPTY_TAGS : new Tags(map);
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

  private final LightweightTagMap map;

  private Tags(Map<String, Set<Object>> map) {
    this.map = new LightweightTagMap(map);
  }

  /** Returns an immutable map containing the tag values. */
  public Map<String, Set<Object>> asMap() {
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
    Map<String, Set<Object>> merged = new TreeMap<String, Set<Object>>();
    for (Map.Entry<String, Set<Object>> e : map.entrySet()) {
      Set<Object> otherValues = other.map.get(e.getKey());
      if (otherValues == null || e.getValue().containsAll(otherValues)) {
        // Our values are a superset of the values in the other map, so just use them.
        merged.put(e.getKey(), e.getValue());
      } else if (otherValues.containsAll(e.getValue())) {
        // The other map's values are a superset of ours, so just use them.
        merged.put(e.getKey(), otherValues);
      } else {
        // Values exist in both maps and neither is a superset, so we really must merge.
        Set<Object> mergedValues = new TreeSet<Object>(VALUE_COMPARATOR);
        mergedValues.addAll(e.getValue());
        mergedValues.addAll(otherValues);
        merged.put(e.getKey(), mergedValues);
      }
    }
    for (Map.Entry<String, Set<Object>> e : other.map.entrySet()) {
      // Finally add those values that were only in the other map.
      if (!map.containsKey(e.getKey())) {
        merged.put(e.getKey(), e.getValue());
      }
    }
    return new Tags(merged);
  }

  @Override
  public boolean equals(@NullableDecl Object obj) {
    return (obj instanceof Tags) && ((Tags) obj).map.equals(map);
  }

  @Override
  public int hashCode() {
    // Invert the bits in the map hashcode just to be different.
    return ~map.hashCode();
  }

  /**
   * Returns human readable representation of the tags. This is not a stable representation and may
   * change over time. If you need to format tags reliably for logging, you should not rely on this
   * method.
   */
  @Override
  public String toString() {
    return map.toString();
  }

  /*
   * A super lightweight, immutable multi-map to hold tag values. The implementation packs all
   * entries and values into a single array, and uses an offset array to jump to the start of each
   * set. Type safety is ensured by careful partitioning during construction of the array.
   *
   * The total allocations for a Tags instance are:
   * 1 x array for entries and values (no duplication, size of map + size of all value sets)
   * 1 x array for offsets (size of the map)
   * N x entries which hold 2 field each (N = size of map)
   * 1 x entry set (holds 1 field)
   *
   * It's about 6 x 32-bits per entry (including object headers) and an extra 32 bits per value. For
   * the largest normal use cases where you have up to 10 values in the tags, one per key, this is
   * under 300 bytes.
   *
   * Previously, using a TreeMap<String, TreeSet<Object>>, it was in the region of 12 x 32 bits per
   * entry and an additional 8 x 32 bits per value (based on examining the source for TreeSet and
   * TreeMap), giving a rough estimate of at least 800 bytes.
   */
  private static class LightweightTagMap extends AbstractMap<String, Set<Object>> {
    // Note if we weren't using binary search for lookup, none of this would be necessary.
    @SuppressWarnings("unchecked")
    private static final Comparator<Object> ENTRY_COMPARATOR =
        new Comparator<Object>() {
          @Override
          public int compare(Object s1, Object s2) {
            // Casting can fail if call passes in unexpected values via Set::contains(entry).
            return ((Entry<String, ?>) s1).getKey().compareTo(((Entry<String, ?>) s2).getKey());
          }
        };

    // The array holds ordered entries followed by values for each entry (grouped by key in order).
    // The offsets array holds the starting offset to each contiguous group of values, plus a final
    // offset to the end of the last group (also the size of the array).
    //
    // [ E(0) ... E(n-1) , V(0,0), V(0,1) ... , V(1,0), V(1,1) ... V(n-1,0), V(n-1,1) ... ]
    // offsets --------[0]-^ ---------------[1]-^ --- ... ---[n-1]-^ -----------------[n]-^
    //
    // E(n) = n-th entry, V(n,m) = m-th value for n-th entry.
    //
    // The entries start at 0 and end at offsets[0].
    // For an entry with index n, its values start at offsets[n] and end at offsets[n+1].
    // It is permitted to have zero values for an entry (i.e. offsets(n) == offsets(n+1)).

    private final Object[] array;
    private final int[] offsets;

    // Reusable, immutable entry set. Index -1 is a slightly special case, see getStart() etc.
    private final Set<Entry<String, Set<Object>>> entrySet =
        new SortedArraySet<Entry<String, Set<Object>>>(-1);

    // Cache these if anyone needs them (not likely in normal usage).
    private Integer hashCode = null;
    private String toString = null;

    LightweightTagMap(Map<String, Set<Object>> map) {
      this.offsets = getOffsetArray(map);
      this.array = getMapArray(map, offsets);
    }

    // Builds the array of start/end offsets to the different sections of the array and determines
    // the total size needed to hold all entries and value efficiently (that's just stored in the
    // final element in the offset array, since it also marks the end of the last group of values).
    private static int[] getOffsetArray(Map<String, Set<Object>> map) {
      int currentSize = map.size();
      // Put a value on the end so we don't have to special case the final entry.
      int[] offsets = new int[currentSize + 1];
      // First value group offset is immediately after the entries.
      offsets[0] = currentSize;
      // Fill in remaining start/end offsets.
      int n = 1;
      for (Set<Object> e : map.values()) {
        currentSize += e.size();
        offsets[n++] = currentSize;
      }
      // Sanity check (in case someone is using the builder instance concurrently).
      checkState(n == offsets.length, "corrupted tag map");
      return offsets;
    }

    // Builds the array in map iteration order, but since this is only called from the builder,
    // which uses sorted sets/maps, this will preserve that order.
    private Object[] getMapArray(Map<String, Set<Object>> map, int[] offsets) {
      Object[] array = new Object[offsets[map.size()]];
      int index = 0;
      // The value offset just increases throughout the loop, starting just after the entries.
      int n = offsets[index];
      for (Entry<String, Set<Object>> e : map.entrySet()) {
        // Store the lightweight entry in the initial part of the array.
        array[index] = newEntry(e.getKey(), index);
        for (Object v : e.getValue()) {
          // Ensure we only store non-null values (should already be promised by the builder).
          array[n++] = checkNotNull(v, "value");
        }
        // Increment the entry index and sanity check that our offset is pointing to the start of
        // the next set of values (or the end of the array if we've just finished the final entry).
        index += 1;
        checkState(n == offsets[index], "corrupted tag map");
      }
      // Sanity check we processed the expected number of entries (should never fail).
      checkState(index == offsets[0], "corrupted tag map");
      return array;
    }

    // Note we could play some tricks and avoid 2 allocations here, but it would mean essentially
    // duplicating the code from SimpleImmutableEntry (so we can merge the entry and values
    // classes). However it's very likely not worth it.
    Map.Entry<String, Set<Object>> newEntry(String key, int index) {
      return new SimpleImmutableEntry<String, Set<Object>>(key, new SortedArraySet<Object>(index));
    }

    @Override
    public Set<Entry<String, Set<Object>>> entrySet() {
      return entrySet;
    }

    /**
     * A lightweight set based on an range in an array. This assumes (but does not enforce) that the
     * elements in the array are ordered according to the comparator. It uses the array and offsets
     * from the outer map class, needing only to specify its index from which start/end offsets can
     * be derived.
     */
    class SortedArraySet<T> extends AbstractSet<T> {
      // -1 = key set, 0...N-1 = values set.
      final int index;

      SortedArraySet(int index) {
        this.index = index;
      }

      private int getStart() {
        return index == -1 ? 0 : offsets[index];
      }

      private int getEnd() {
        return offsets[index + 1];
      }

      private Comparator<Object> getComparator() {
        return index == -1 ? ENTRY_COMPARATOR : VALUE_COMPARATOR;
      }

      @Override
      public int size() {
        return getEnd() - getStart();
      }

      // Optional, but potentially faster to binary search for elements.
      // TODO(dbeaumont): Benchmark for realistic tag usage and consider removing.
      @Override
      public boolean contains(Object o) {
        return Arrays.binarySearch(array, getStart(), getEnd(), o, getComparator()) >= 0;
      }

      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          private int n = 0;

          @Override
          public boolean hasNext() {
            return n < size();
          }

          @Override
          @SuppressWarnings("unchecked")
          public T next() {
            // Copy to local variable to guard against concurrent calls to next() causing the index
            // to become currupted and going off the end of the valid range for this iterator.
            // This doesn't make concurrent iteration thread safe in general, but prevents weird
            // situations where a value for a different element could be returned by mistake.
            int idx = n;
            if (idx < size()) {
              T value = (T) array[getStart() + idx];
              // Written value is never > size(), even with concurrent iteration.
              n = idx + 1;
              return value;
            }
            throw new NoSuchElementException();
          }
        };
      }
    }

    @Override
    public int hashCode() {
      // Abstract maps cannot cache their hash codes, but we know we're immutable, so we can.
      if (hashCode == null) {
        hashCode = super.hashCode();
      }
      return hashCode;
    }

    @Override
    public String toString() {
      if (toString == null) {
        toString = super.toString();
      }
      return toString;
    }
  }
}
