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

import static com.google.common.flogger.util.Checks.checkArgument;
import static com.google.common.flogger.util.Checks.checkMetadataIdentifier;
import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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

  // Note: This is just a "dumb" holder and doesn't have equals/hashcode defined.
  private static final class KeyValuePair {
    private final String key;
    @NullableDecl private final Object value;

    private KeyValuePair(String key, @NullableDecl Object value) {
      this.key = key;
      this.value = value;
    }
  }

  // A stylistic choice to make the comparator separate, rather than have KeyValuePair implement
  // Comparable, because a class which implements Comparable is usually implicitly expected to
  // also have sensible equals/hashCode methods, but we don't need those.
  private static final Comparator<KeyValuePair> KEY_VALUE_COMPARATOR =
      new Comparator<KeyValuePair>() {
        @Override
        public int compare(KeyValuePair lhs, KeyValuePair rhs) {
          int signum = lhs.key.compareTo(rhs.key);
          if (signum == 0) {
            if (lhs.value != null) {
              signum = rhs.value != null ? VALUE_COMPARATOR.compare(lhs.value, rhs.value) : 1;
            } else {
              signum = rhs.value != null ? -1 : 0;
            }
          }
          return signum;
        }
      };

  private static final Tags EMPTY_TAGS =
      new Tags(new LightweightTagMap(Collections.<KeyValuePair>emptyList()));

  /** A mutable builder for tags. */
  public static final class Builder {
    private final List<KeyValuePair> keyValuePairs = new ArrayList<KeyValuePair>();

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
      return addImpl(name, null);
    }

    /**
     * Adds a string value for the given name, ensuring that the values for the given name contain
     * at least this value. Adding the same name/value pair more than once has no effect.
     */
    @CanIgnoreReturnValue
    public Builder addTag(String name, String value) {
      checkArgument(value != null, "tag value");
      return addImpl(name, value);
    }

    /**
     * Adds a boolean value for the given name, ensuring that the values for the given name contain
     * at least this value. Adding the same name/value pair more than once has no effect.
     */
    @CanIgnoreReturnValue
    public Builder addTag(String name, boolean value) {
      return addImpl(name, value);
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
      return addImpl(name, value);
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
      return addImpl(name, value);
    }

    @CanIgnoreReturnValue
    private Builder addImpl(String name, @NullableDecl Object value) {
      keyValuePairs.add(new KeyValuePair(checkMetadataIdentifier(name), value));
      return this;
    }

    /** Returns an immutable tags instance. */
    public Tags build() {
      if (keyValuePairs.isEmpty()) {
        return EMPTY_TAGS;
      }
      // Safe, even for a reused builder, because we never care about original value order. We
      // could deduplicate here to guard against pathological use, but it should never matter.
      Collections.sort(keyValuePairs, KEY_VALUE_COMPARATOR);
      return new Tags(new LightweightTagMap(keyValuePairs));
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
    return new Tags(name, value);
  }

  /**
   * Returns a single tag without needing to use the builder API. Where multiple tags are needed, it
   * is always better to use the builder directly.
   */
  public static Tags of(String name, boolean value) {
    return new Tags(name, value);
  }

  /**
   * Returns a single tag without needing to use the builder API. Where multiple tags are needed, it
   * is always better to use the builder directly.
   */
  public static Tags of(String name, long value) {
    return new Tags(name, value);
  }

  /**
   * Returns a single tag without needing to use the builder API. Where multiple tags are needed, it
   * is always better to use the builder directly.
   */
  public static Tags of(String name, double value) {
    return new Tags(name, value);
  }

  private final LightweightTagMap map;

  // Called for singleton Tags instances (but we need to check arguments here).
  private Tags(String name, Object value) {
    this(new LightweightTagMap(checkMetadataIdentifier(name), checkNotNull(value, "value")));
  }

  // Canonical constructor, also called from merge().
  private Tags(LightweightTagMap map) {
    this.map = map;
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
    // We could check if they are equal or one is a subset of the other, but we *really* don't
    // expect that to be a common situation and merging should be fast enough.
    return new Tags(new LightweightTagMap(map, other.map));
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

    // A heuristic used when deciding to resize element or offset arrays. Arrays above this size
    // will give savings when resized by more than 10%. In this code, the maximum saving is 50% of
    // the array size, so arrays at or below this limit could only be wasting at most half this
    // value of elements.
    private static final int SMALL_ARRAY_LENGTH = 16;

    // A singleton map always has the same immutable offsets (start/end value indices).
    private static final int[] singletonOffsets = new int[] {1, 2};

    // This array holds ordered entries followed by values for each entry (grouped by key in order).
    //
    // The offsets array holds the starting offset to each contiguous group of values, plus a final
    // offset to the end of the last group (but we allow sloppy array sizing, so there might be
    // unused elements after the end of the last group and the array size is not to be trusted).
    //
    // [ E(0) ... E(n-1) , V(0,0), V(0,1) ... , V(1,0), V(1,1) ... V(n-1,0), V(n-1,1) ... xxx ... ]
    // offsets --------[0]-^ ---------------[1]-^ --- ... ---[n-1]-^ -----------------[n]-^
    //
    // E(n) = n-th entry, V(n,m) = m-th value for n-th entry.
    //
    // The set of entries has index -1, and entries start at 0 and end at offsets[0].
    //
    // For an entry with index n >= 0, the values start at offsets[n] and end at offsets[n+1].
    // It is permitted to have zero values for an entry (i.e. offsets(n) == offsets(n+1)).
    private final Object[] array;
    private final int[] offsets;

    // Reusable, immutable entry set. Index -1 is a slightly special case, see getStart() etc.
    private final Set<Entry<String, Set<Object>>> entrySet =
        new SortedArraySet<Entry<String, Set<Object>>>(-1);

    // Cache these if anyone needs them (not likely in normal usage).
    private Integer hashCode = null;
    private String toString = null;

    // ---- Singleton constructor ----

    LightweightTagMap(String name, Object value) {
      this.offsets = singletonOffsets;
      this.array = new Object[] {newEntry(name, 0), value};
    }

    // ---- General constructor ----

    LightweightTagMap(List<KeyValuePair> sortedPairs) {
      // Allocate the maximum required space for entries and values. This is a bit wasteful if there
      // are pairs with null values in (rare) or duplicates (very rare) but we might resize later.
      int entryCount = countMapEntries(sortedPairs);
      Object[] array = new Object[entryCount + sortedPairs.size()];
      int[] offsets = new int[entryCount + 1];

      int totalElementCount = makeTagMap(sortedPairs, entryCount, array, offsets);
      this.array = maybeResizeElementArray(array, totalElementCount);
      this.offsets = offsets;
    }

    // ---- Merging constructor ----

    LightweightTagMap(LightweightTagMap lhs, LightweightTagMap rhs) {
      // We already checked that neither map is empty and it's probably not worth optimizing for the
      // case where one is a subset of the other (by the time you've checked you might as well have
      // just made a new instance anyway).
      //
      // We expect to efficiently use most or all of this array (resizing should be rare).
      int maxEntryCount = lhs.size() + rhs.size();
      Object[] array = new Object[lhs.getTotalElementCount() + rhs.getTotalElementCount()];
      int[] offsets = new int[maxEntryCount + 1];

      int totalElementCount = mergeTagMaps(lhs, rhs, maxEntryCount, array, offsets);
      this.array = adjustOffsetsAndMaybeResize(array, offsets, totalElementCount);
      this.offsets = maybeResizeOffsetsArray(offsets);
    }

    // ---- Helpers for making a tag map from the builder. ----

    // Count the unique keys for a sorted list of key-value pairs.
    private static int countMapEntries(List<KeyValuePair> sortedPairs) {
      String key = null;
      int count = 0;
      for (KeyValuePair pair : sortedPairs) {
        if (!pair.key.equals(key)) {
          key = pair.key;
          count++;
        }
      }
      return count;
    }

    // Processes a sorted sequence of key/value pairs to fill the given arrays/offsets. This is a
    // single pass of the pairs
    private int makeTagMap(
        List<KeyValuePair> sortedPairs, int entryCount, Object[] array, int[] offsets) {
      String key = null;
      Object value = null;
      int newEntryIndex = 0;
      int valueStart = entryCount;
      for (KeyValuePair pair : sortedPairs) {
        if (!pair.key.equals(key)) {
          key = pair.key;
          array[newEntryIndex] = newEntry(key, newEntryIndex);
          offsets[newEntryIndex] = valueStart;
          newEntryIndex++;
          value = null;
        }
        if (pair.value != null && !pair.value.equals(value)) {
          value = pair.value;
          array[valueStart++] = value;
        }
      }
      // If someone was using the builder concurrently, all bets are off.
      if (newEntryIndex != entryCount) {
        throw new ConcurrentModificationException("corrupted tag map");
      }
      offsets[entryCount] = valueStart;
      return valueStart;
    }

    // ---- Helpers for merging tag maps. ----

    private int mergeTagMaps(
        LightweightTagMap lhs,
        LightweightTagMap rhs,
        int maxEntryCount,
        Object[] array,
        int[] offsets) {
      // Merge values starting at the first safe offset after the largest possible number of
      // entries. We may need to copy elements later to remove any gap due to duplicate keys.
      // If the values are copied down we must remember to re-adjust the offsets as well.
      int valueStart = maxEntryCount;
      // The first offset is the start of the first values segment.
      offsets[0] = valueStart;

      // We must have at least one entry per map, but they can be null once we run out.
      int lhsEntryIndex = 0;
      Entry<String, SortedArraySet<Object>> lhsEntry = lhs.getEntryOrNull(lhsEntryIndex);
      int rhsEntryIndex = 0;
      Entry<String, SortedArraySet<Object>> rhsEntry = rhs.getEntryOrNull(rhsEntryIndex);

      int newEntryIndex = 0;
      while (lhsEntry != null || rhsEntry != null) {
        // Nulls count as being *bigger* than anything (since they indicate the end of the array).
        int signum = (lhsEntry == null) ? 1 : (rhsEntry == null) ? -1 : 0;
        if (signum == 0) {
          // Both entries exist and must be compared.
          signum = lhsEntry.getKey().compareTo(rhsEntry.getKey());
          if (signum == 0) {
            // Merge values, update both indices/entries.
            array[newEntryIndex] = newEntry(lhsEntry.getKey(), newEntryIndex);
            newEntryIndex++;
            valueStart = mergeValues(lhsEntry.getValue(), rhsEntry.getValue(), array, valueStart);
            offsets[newEntryIndex] = valueStart;
            lhsEntry = lhs.getEntryOrNull(++lhsEntryIndex);
            rhsEntry = rhs.getEntryOrNull(++rhsEntryIndex);
            continue;
          }
        }
        // Signum is non-zero and indicates which entry to process next (without merging).
        if (signum < 0) {
          valueStart = copyEntryAndValues(lhsEntry, newEntryIndex++, valueStart, array, offsets);
          lhsEntry = lhs.getEntryOrNull(++lhsEntryIndex);
        } else {
          valueStart = copyEntryAndValues(rhsEntry, newEntryIndex++, valueStart, array, offsets);
          rhsEntry = rhs.getEntryOrNull(++rhsEntryIndex);
        }
      }
      return newEntryIndex;
    }

    // Called when merging maps to merge the values for a pair of entries with duplicate keys.
    private static int mergeValues(
        SortedArraySet<?> lhs, SortedArraySet<?> rhs, Object[] array, int valueStart) {
      // The indices here are the value indices within the lhs/rhs elements, not the indices of the
      // elements in their respective maps, but the basic loop structure is very similar.
      int lhsIndex = 0;
      int rhsIndex = 0;
      while (lhsIndex < lhs.size() || rhsIndex < rhs.size()) {
        int signum = (lhsIndex == lhs.size()) ? 1 : (rhsIndex == rhs.size()) ? -1 : 0;
        if (signum == 0) {
          signum = VALUE_COMPARATOR.compare(lhs.getValue(lhsIndex), rhs.getValue(rhsIndex));
        }
        // Signum can be zero here for duplicate values (unlike the entry processing loop above).
        Object value;
        if (signum < 0) {
          value = lhs.getValue(lhsIndex++);
        } else {
          value = rhs.getValue(rhsIndex++);
          if (signum == 0) {
            // Equal values means we just drop the duplicate.
            lhsIndex++;
          }
        }
        array[valueStart++] = value;
      }
      return valueStart;
    }

    // Called when merging maps to copy an entry with a unique key, and all its values.
    private int copyEntryAndValues(
        Map.Entry<String, SortedArraySet<Object>> entry,
        int entryIndex,
        int valueStart,
        Object[] array,
        int[] offsets) {
      SortedArraySet<Object> values = entry.getValue();
      int valueCount = values.getEnd() - values.getStart();
      System.arraycopy(values.getValuesArray(), values.getStart(), array, valueStart, valueCount);
      array[entryIndex] = newEntry(entry.getKey(), entryIndex);
      // Record the end offset for the segment, and return it as the start of the next segment.
      int valueEnd = valueStart + valueCount;
      offsets[entryIndex + 1] = valueEnd;
      return valueEnd;
    }

    // Called after merging two maps to see if the offset array needs adjusting.
    // This method may also "right size" the values array if it detected sufficient wastage.
    private static Object[] adjustOffsetsAndMaybeResize(
        Object[] array, int[] offsets, int entryCount) {
      // See if there's a gap between entries and values (due to duplicate keys being merged).
      // If not then we know that the array uses all its elements (since no values were merged).
      int maxEntries = offsets[0];
      int offsetReduction = maxEntries - entryCount;
      if (offsetReduction == 0) {
        return array;
      }
      for (int i = 0; i <= entryCount; i++) {
        offsets[i] -= offsetReduction;
      }
      Object[] dstArray = array;
      int totalElementCount = offsets[entryCount];
      int valueCount = totalElementCount - entryCount;
      if (mustResize(array.length, totalElementCount)) {
        dstArray = new Object[totalElementCount];
        System.arraycopy(array, 0, dstArray, 0, entryCount);
      }
      // If we are reusing the working array, this copy leaves non-null values in the unused
      // portion at the end of the array, but these references are also repeated earlier in the
      // array, so there's no issue with leaking any values because of this.
      System.arraycopy(array, maxEntries, dstArray, entryCount, valueCount);
      return dstArray;
    }

    // ---- General constructor helper methods ----

    // Resize the value array if necessary.
    private static Object[] maybeResizeElementArray(Object[] array, int bestLength) {
      return mustResize(array.length, bestLength) ? Arrays.copyOf(array, bestLength) : array;
    }

    // Resize the value array if necessary (separate since int[] and Object[] are not compatible).
    private static int[] maybeResizeOffsetsArray(int[] offsets) {
      // Remember we must account for the extra final offset (the end of the final segment).
      int bestLength = offsets[0] + 1;
      return mustResize(offsets.length, bestLength) ? Arrays.copyOf(offsets, bestLength) : offsets;
    }

    // Common logic to decide if we're wasting too much off an array and need to "right size" it.
    // Returns true if more than 10% wasted in a non-trivial sized array.
    private static boolean mustResize(int actualLength, int bestLength) {
      return actualLength > SMALL_ARRAY_LENGTH && (9 * actualLength > 10 * bestLength);
    }

    // Returns a new entry for this map with the given key and values read according to the
    // specified offset index (see SortedArraySet).
    private Map.Entry<String, SortedArraySet<Object>> newEntry(String key, int index) {
      return new SimpleImmutableEntry<String, SortedArraySet<Object>>(
          key, new SortedArraySet<Object>(index));
    }

    @SuppressWarnings("unchecked") // Safe when the index is in range.
    private Map.Entry<String, SortedArraySet<Object>> getEntryOrNull(int index) {
      return index < offsets[0] ? (Map.Entry<String, SortedArraySet<Object>>) array[index] : null;
    }

    // Returns the total number of used elements in the entry/value array. Note that this may well
    // be less than the total array size, since we allow for "sloppy" resizing.
    private int getTotalElementCount() {
      return offsets[size()];
    }

    // ---- Public API methods. ----

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

      Object[] getValuesArray() {
        return array;
      }

      // Caller must check 0 <= n < size().
      Object getValue(int n) {
        return array[getStart() + n];
      }

      int getStart() {
        return index == -1 ? 0 : offsets[index];
      }

      int getEnd() {
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
          public T next() {
            // Copy to local variable to guard against concurrent calls to next() causing the index
            // to become corrupted, and going off the end of the valid range for this iterator.
            // This doesn't make concurrent iteration thread safe in general, but prevents weird
            // situations where a value for a different element could be returned by mistake.
            int index = n;
            if (index < size()) {
              @SuppressWarnings("unchecked")
              T value = (T) array[getStart() + index];
              // Written value is never > size(), even with concurrent iteration.
              n = index + 1;
              return value;
            }
            throw new NoSuchElementException();
          }

          @Override // in case we are on an earlier Java version with no default method for this
          public void remove() {
            throw new UnsupportedOperationException();
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
