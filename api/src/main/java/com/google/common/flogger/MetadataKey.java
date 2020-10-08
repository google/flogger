/*
 * Copyright (C) 2018 The Flogger Authors.
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

package com.google.common.flogger;

import static com.google.common.flogger.util.Checks.checkMetadataIdentifier;
import static com.google.common.flogger.util.Checks.checkNotNull;

/**
 * Key for logging semi-structured metadata values.
 *
 * <p>Metadata keys can be used to provide log statements with strongly typed values which can be
 * read and interpreted by logging backends or other logs related tools. This mechanism is intended
 * for values with specific semantics and should not be seen as a replacement for logging arguments
 * as part of a formatted log message.
 *
 * <p>Logger backends can act upon metadata present in log statements to modify behaviour. Any
 * metadata entries that are not handled by a backend explicitly are, by default, rendered as part
 * of the log statement in a default format.
 *
 * <p>Note that some metadata entries are handled prior to being processed by the backend (e.g. rate
 * limiting), but a metadata entry remains present to record the fact that rate limiting was
 * enabled.
 */
public class MetadataKey<T> {
  /**
   * Callback interface to handle additional contextual {@code Metadata} in log statements. This
   * interface is only intended to be implemented by logger backend classes as part of handling
   * metadata, and should not be used in any general application code, other than to implement the
   * {@link MetadataKey#emit} method in this class.
   */
  public interface KeyValueHandler {
    /** Handle a single key/value pair of contextual metadata for a log statement. */
    void handle(String key, Object value);
  }

  /**
   * Creates a key for a single piece of metadata. If metadata is set more than once using this
   * key for the same log statement, the last set value will be the one used, and other values
   * will be ignored (although callers should never rely on this behavior).
   * <p>
   * Key instances behave like singletons, and two key instances with the same label will still
   * be considered distinct. The recommended approach is to always assign {@code MetadataKey}
   * instances to static final constants.
   */
  public static <T> MetadataKey<T> single(String label, Class<T> clazz) {
    return new MetadataKey<T>(label, clazz, false);
  }

  /**
   * Creates a key for a repeated piece of metadata. If metadata is added more than once using
   * this key for a log statement, all values will be retained as key/value pairs in the order
   * they were added.
   * <p>
   * Key instances behave like singletons, and two key instances with the same label will still
   * be considered distinct. The recommended approach is to always assign {@code MetadataKey}
   * instances to static final constants.
   */
  public static <T> MetadataKey<T> repeated(String label, Class<T> clazz) {
    return new MetadataKey<T>(label, clazz, true);
  }

  private final String label;
  private final Class<T> clazz;
  private final boolean canRepeat;
  private final long bloomFilterMask;

  /**
   * Constructor for custom key subclasses. Most use-cases will not require the use of custom keys,
   * but occasionally it can be useful to create a specific subtype to control the formatting of
   * values or to have a family of related keys with a common parent type.
   */
  protected MetadataKey(String label, Class<T> clazz, boolean canRepeat) {
    this.label = checkMetadataIdentifier(label);
    this.clazz = checkNotNull(clazz, "class");
    this.canRepeat = canRepeat;
    this.bloomFilterMask = createBloomFilterMaskFromSystemHashcode();
  }

  /**
   * Returns a short, human readable text label which will prefix the metadata in cases where it
   * is formatted as part of the log message.
   */
  public final String getLabel() {
    return label;
  }

  /** Cast an arbitrary value to the type of this key. */
  public final T cast(Object value) {
    return clazz.cast(value);
  }

  /** Whether this key can be used to set more than one value in the metadata. */
  public final boolean canRepeat() {
    return canRepeat;
  }

  /**
   * Emits one or more key/value pairs for the given metadata value. By default this method simply
   * emits the given value with this key's label, but it can be overridden to emit multiple
   * key/value pairs if necessary. Note that if multiple key/value pairs are emitted, the following
   * best-practice should be followed:
   * <ul>
   *   <li>Key names should be of the form {@code "<label>.<suffix>"}.
   *   <li>Suffixes may only contain lower case ASCII letters and underscore (i.e. [a-z_]).
   * </ul>
   */
  public void emit(Object value, KeyValueHandler out) {
    out.handle(getLabel(), value);
  }

  /**
   * Returns a 64-bit bloom filter mask for this metadata key, usable by backend implementations to
   * efficiently determine uniqueness of keys (e.g. for deduplication and grouping). This value is
   * calculated on the assumption that there are normally not more than 10 distinct metadata keys
   * being processed at any time. If more distinct keys need to be processed using this Bloom Filter
   * mask, it will result in a higher than optimal false-positive rate.
   */
  public final long getBloomFilterMask() {
    return bloomFilterMask;
  }

  // Prevent subclasses changing the singleton semantics of keys.
  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  // Prevent subclasses using toString() for anything unexpected.
  @Override
  public final String toString() {
    return getClass().getName() + "/" + label + "[" + clazz.getName() + "]";
  }

  // From https://en.wikipedia.org/wiki/Bloom_filter the number of hash bits to minimize false
  // positives is:
  //   k = (M / N) ln(2)
  // where:
  //   k = number of "hash functions" which in our case is the number of bits in the filter mask.
  //   M = number of bits available (in our case 64)
  //   N = number of elements in the array (variable but almost always < 10)
  // This gives a bit count of ~5 bits per mask, which is convenient since that's easily available
  // by just masking out successive 6-bit chunks in a 32 bit hashcode.
  private long createBloomFilterMaskFromSystemHashcode() {
    // In tests (JDK11) the identity hashcode on its own was as good, if not better than, applying
    // a "mix" operation such as found in:
    // https://github.com/google/guava/blob/master/guava/src/com/google/common/hash/Murmur3_32HashFunction.java#L234
    int hash = System.identityHashCode(this);
    long bloom = 0L;
    // Bottom 6-bits form a value from 0-63 (the bit index in the Bloom Filter), and we can extract
    // 5 of these for a 32-bit value (see above for why 5 bits per mask is enough).
    for (int n = 0; n < 5; n++) {
      bloom |= 1L << (hash & 0x3F);
      hash >>>= 6;
    }
    return bloom;
  }
}
