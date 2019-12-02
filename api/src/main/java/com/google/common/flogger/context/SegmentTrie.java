/*
 * Copyright (C) 2019 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.checkNotNull;
import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A fast prefix-Trie implementation for segmented keys. For example given the mapping:
 *
 * <pre>{@code
 * "foo" = FOO
 * "foo.bar" = FOO_BAR
 * }</pre>
 *
 * (where {@code '.'} is the segment separator) and a default value of {@code DEFAULT}, we get:
 *
 * <ul>
 *   <li>{@code find("foo") == FOO} (exact match)
 *   <li>{@code find("foo.bar") == FOO_BAR} (exact match)
 *   <li>{@code find("foo.foo") == FOO} (nearest parent)
 *   <li>{@code find("bar") == DEFAULT} (no match)
 * </ul>
 *
 * <p>This implementation supports empty segments (e.g. keys like {@code ""} or {@code "..."})
 * correctly and never allocates any memory during lookup. It also supports {@code null} values, but
 * will not continue to look for a parent match if it finds one (if you want a mapping to be
 * ignored, don't include it in the map at all).
 *
 * <p>This implementation was designed for high performance in situations in which the key/value map
 * is small compared to the set of possible search keys and the likelihood of finding an exact match
 * is relatively low (i.e. most lookups will match the nearest parent or not match anything).
 *
 * <p>This implementation is immutable and thread safe (the given map is copied during the
 * construction of the Trie, and subsequent changes to the map are not reflected in the Trie).
 *
 * <p>Limitations: Separators are chars only (not Unicode code-points or strings) and cannot
 * represent anything outside the basic-multilingual plane (e.g. no string or Emoji separators).
 */
// This class could easily be made a shareable utility class if need by anyone else.
abstract class SegmentTrie<T> {
  /**
   * Returns a prefix Trie for the given mapping, where keys are segmented via the given separator.
   */
  public static <T> SegmentTrie<T> create(
      Map<String, ? extends T> map, char separator, T defaultValue) {
    switch (map.size()) {
      case 0:
        return new EmptyTrie<T>(defaultValue);
      case 1:
        Map.Entry<String, ? extends T> e = map.entrySet().iterator().next();
        return new SingletonTrie<T>(e.getKey(), e.getValue(), separator, defaultValue);
      default:
        return new SortedTrie<T>(map, separator, defaultValue);
    }
  }

  private final T defaultValue;

  SegmentTrie(T defaultValue) {
    this.defaultValue = defaultValue;
  }

  public final T getDefaultValue() {
    return defaultValue;
  }

  /** Returns the value of the entry which most closely matches the given key. */
  public abstract T find(String key);

  /** Returns an immutable view of the entries in this Trie. */
  public abstract Map<String, T> getEntryMap();

  // Trivial implementation for the empty map (always return the default value).
  private static final class EmptyTrie<T> extends SegmentTrie<T> {
    EmptyTrie(T defaultValue) {
      super(defaultValue);
    }

    @Override
    public T find(String k) {
      return getDefaultValue();
    }

    @Override
    public Map<String, T> getEntryMap() {
      return Collections.emptyMap();
    }
  }

  // Trivial implementation for a map with one entry.
  private static final class SingletonTrie<T> extends SegmentTrie<T> {
    private final String key;
    private final T value;
    private final char separator;

    SingletonTrie(String key, T value, char separator, T defaultValue) {
      super(defaultValue);
      this.key = checkNotNull(key, "key");
      this.value = value;
      this.separator = separator;
    }

    @Override
    public T find(String k) {
      // Remember that just being a prefix isn't enough, it must match up to the end of a segment.
      return k.regionMatches(0, key, 0, key.length())
              && (k.length() == key.length() || k.charAt(key.length()) == separator)
          ? value
          : getDefaultValue();
    }

    @Override
    public Map<String, T> getEntryMap() {
      Map<String, T> map = new HashMap<String, T>();
      map.put(key, value);
      return Collections.unmodifiableMap(map);
    }
  }

  // General purpose implementation using a custom binary search to reduce repeated re-comparing of
  // keys. Nothing in or called by the "find" method is allowed to allocate any memory.
  private static final class SortedTrie<T> extends SegmentTrie<T> {
    private final String[] keys;
    private final List<T> values;
    private final int[] parent;
    private final char separator;

    SortedTrie(Map<String, ? extends T> entries, char separator, T defaultValue) {
      super(defaultValue);
      TreeMap<String, T> sorted = new TreeMap<String, T>(entries);
      this.keys = sorted.keySet().toArray(new String[0]);
      this.values = new ArrayList<T>(sorted.values());
      this.parent = buildParentMap(keys, separator);
      this.separator = separator;
    }

    @Override
    public T find(String key) {
      int keyLen = key.length();

      // Find the left-hand-side bound and get the size of the common prefix.
      int lhsIdx = 0;
      int lhsPrefix = prefixCompare(key, keys[lhsIdx], 0);
      if (lhsPrefix == keyLen) {
        // If equal, just return the element.
        return values.get(lhsIdx);
      }
      if (lhsPrefix < 0) {
        // If the key is before the first element it has no parent.
        return getDefaultValue();
      }

      // Find the right-hand-side bound and get the size of the common prefix.
      int rhsIdx = keys.length - 1;
      int rhsPrefix = prefixCompare(key, keys[rhsIdx], 0);
      if (rhsPrefix == keyLen) {
        // If equal, just return the element.
        return values.get(rhsIdx);
      }
      if (rhsPrefix >= 0) {
        // If the key is after the last element it may have a parent.
        return findParent(key, rhsIdx, rhsPrefix);
      }
      // If rhsPrefix is negative, it's the bitwise-NOT of what we want.
      rhsPrefix = ~rhsPrefix;

      // Binary search: At the top of the loop, lhsPrefix & rhsPrefix are positive.
      while (true) {
        // Determine the pivot index.
        // NOTE: In theory we might be able to improve performance by biasing the pivot index
        // towards the side with the larger common prefix length.
        int midIdx = (lhsIdx + rhsIdx) >>> 1;
        if (midIdx == lhsIdx) {
          // No match found: The left-hand-side is the nearest lexicographical entry (but not
          // equal), but we know that if the search key has a parent in the trie, then it must be
          // a parent of this entry (even if this entry is not a direct sibling).
          return findParent(key, lhsIdx, lhsPrefix);
        }
        // Find the prefix length of the pivot value (using the minimum prefix length of the
        // current bounds to limit the work done).
        int midPrefix = prefixCompare(key, keys[midIdx], min(lhsPrefix, rhsPrefix));
        if (keyLen == midPrefix) {
          // If equal, just return the element.
          return values.get(midIdx);
        }
        if (midPrefix >= 0) {
          // key > pivot, so reset left-hand bound
          lhsIdx = midIdx;
          lhsPrefix = midPrefix;
        } else {
          // key < pivot, so reset right-hand bound
          rhsIdx = midIdx;
          rhsPrefix = ~midPrefix;
        }
      }
    }

    /**
     * Finds the value of the nearest parent of the given key, starting at the element
     * lexicographically preceding the key (but which is not equal to the key).
     *
     * @param k the key whose parent value we wish to find.
     * @param idx the index of the closest matching key in the trie ({@code k < keys[idx]}).
     * @param len the common prefix length between {@code k} and {@code keys[idx]}.
     * @return the value of the nearest parent of {@code k}.
     */
    private T findParent(String k, int idx, int len) {
      while (!isParent(keys[idx], k, len)) {
        idx = parent[idx];
        if (idx == -1) {
          return getDefaultValue();
        }
      }
      return values.get(idx);
    }

    /**
     * Determines if a given candidate value {@code p} is the parent of a key {@code k}.
     *
     * <p>We know that {@code p < k} (lexicographically) and (importantly) {@code p != k}. We also
     * know that {@code len} is common prefix length.
     *
     * <p>Thus either:
     *
     * <ul>
     *   <li>The common prefix is a strict prefix of k (i.e. {@code k.length() > len}).
     *   <li>The common prefix is equal to {@code k}, but {@code p} must be longer (or else {@code p
     *       == k}).
     * </ul>
     *
     * Thus if {@code (p.length <= len)} then {@code (k.length() > p.length())}.
     *
     * @param p the candidate parent key to check.
     * @param k the key whose parent we are looking for.
     * @param len the maximum length of any possible parent of {@code k}.
     */
    private boolean isParent(String p, String k, int len) {
      return p.length() <= len && k.charAt(p.length()) == separator;
    }

    /**
     * Returns the common prefix between two strings, encoding the returned value to indicate
     * lexicographical order. That is:
     *
     * <ul>
     *   <li>If {@code lhs >= rhs}, the returned value is the common prefix length.
     *   <li>If {@code lhs < rhs}, the returned value is the bitwise-NOT of the common prefix
     *       length.
     * </ul>
     *
     * <p>This permits the function to be used for both comparison, and for determining the common
     * prefix length (if the returned prefix length is non-negative and equal to {@code
     * lhs.length()} then {@code lhs == rhs}).
     *
     * <p>By allowing a known existing lower bound for the prefix length to be provided, this method
     * can skip re-comparing the beginning of values repeatedly when used in a binary search. The
     * given lower bound value is expected to be the result of previous calls the this function (or
     * {@code 0}).
     *
     * @param lhs first value to compare.
     * @param rhs second value to compare.
     * @param start a lower bound for the common prefix length of the given keys, which must be
     *     {@code <= min(lhs.length(), rhs.length())}.
     * @return the common prefix length, encoded to indicate lexicographical ordering.
     */
    private static int prefixCompare(String lhs, String rhs, int start) {
      if (start < 0) {
        throw new IllegalStateException("lhs=" + lhs + ", rhs=" + rhs + ", start=" + start);
      }
      int len = min(lhs.length(), rhs.length());
      for (int n = start; n < len; n++) {
        int diff = lhs.charAt(n) - rhs.charAt(n);
        if (diff != 0) {
          return diff < 0 ? ~n : n;
        }
      }
      return (len < rhs.length()) ? ~len : len;
    }

    /**
     * Builds an index mapping array {@code pmap} such that {@code pmap[idx]} is the index of the
     * parent element of {@code keys[idx]}, or {@code -1} if no parent exists.
     */
    private static int[] buildParentMap(String[] keys, char separator) {
      int[] pmap = new int[keys.length];
      // The first key cannot have a parent.
      pmap[0] = -1;
      for (int n = 1; n < keys.length; n++) {
        // Assume no parent will be found (just makes things a bit easier later).
        pmap[n] = -1;
        // Generate each parent key in turn until a match is found.
        String key = keys[n];
        for (int sidx = key.lastIndexOf(separator); sidx >= 0; sidx = key.lastIndexOf(separator)) {
          key = key.substring(0, sidx);
          int i = Arrays.binarySearch(keys, 0, n, key);
          if (i >= 0) {
            // Match found, so set index and exit.
            pmap[n] = i;
            break;
          }
        }
      }
      return pmap;
    }

    @Override
    public Map<String, T> getEntryMap() {
      Map<String, T> map = new LinkedHashMap<String, T>();
      for (int n = 0; n < keys.length; n++) {
        map.put(keys[n], values.get(n));
      }
      return Collections.unmodifiableMap(map);
    }
  }
}
