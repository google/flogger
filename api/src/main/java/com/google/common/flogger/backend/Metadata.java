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

package com.google.common.flogger.backend;

import com.google.common.flogger.MetadataKey;
import javax.annotation.Nullable;

/**
 * Optional debug information which can be attached to a log statement. Metadata is represented as
 * a sequence of key/value pairs and can be attached to log statements to provide additional
 * contextual information.
 * <p>
 * Metadata keys are two-part, slash-separated, strings which indicate the semantics of the
 * associated value. The general form is {@code "namespace/type-descriptor"}.
 * <p>
 * The first part of a key is a non-empty namespace, unique to a specific API implementation. If a
 * custom logging API extension wishes to ensure uniqueness of its key prefix it should use the
 * dot-separated, reversed domain notation typically used for Java package names (e.g.
 * "com.example.project/type"). Note that the core logging classes reserve the use of the empty
 * string as root namespace for keys (i.e. "/type").
 * <p>
 * The second part of a key is an abstract type descriptor that's private to the namespace. It is
 * recommended that type identifiers are restricted to simple, human readable values as they may
 * appear in log output.
 * <p>
 * If a logging backend does not specifically know about a given key it can do anything it wants
 * with it, including discarding it (though this is highly discouraged). Where possible a logging
 * backend should, at a minimum, output the formatted metadata in some simple tabular form, using
 * the type descriptor and metadata value.
 * <p>
 * For example consider an extended logging API provided by the package "com.example.logging" which
 * provides the {@code withProbability(double p)} method to allow randomized sampling of log
 * statements. The extended logging API could store the probability value directly as a
 * {@code java.lang.Double} instance, or it could wrap it in an immutable value object to provide
 * a more reasonably formatted {@code toString()} implementation. The wrapped metadata value could
 * provide additional methods for backends which understand this metadata. This value is then
 * saved using the metadata key {@code "com.example/probability"}.
 * <p>
 * If a backend does not know how to interpret this key directly, it can output the value in
 * tabular form prefixed by the type name, yielding something like:
 * <pre>{@code
 *   probability: 0.3
 * }</pre>
 * or, if a wrapper value was used to customize formatting:
 * <pre>{@code
 *   probability: p=0.30
 * }</pre>
 * <p>
 * It is important that extended logging APIs keep the wrapper classes and metadata keys stable as
 * backends may expect certain Java types to be used for specific metadata keys. However it is
 * vital that backend implementations can always fall back to some kind of tabular output (rather
 * than causing a runtime error) if metadata values are not of the expected type.
 * <p>
 * Note also that keys will occur in an arbitrary order and can be repeated within a single metadata
 * sequence, and it is up to the backend as to how it deals with this. Specific logging API
 * extensions may chose to enforce additional guarantees about the uniqueness of some metadata, but
 * this is by convention only. Where a backend expects only a single value to be present for a
 * certain type of metadata it is sufficient for it to select the first value present using
 * {@link #findValue(MetadataKey)}.
 * <p>
 * Finally, as the metadata keys should be unique, it is strongly encouraged that all keys are
 * defined as string literals.
 */
public abstract class Metadata {

  /** Returns an immutable {@link Metadata} that has no items. */
  public static Metadata empty() {
    return Empty.INSTANCE;
  }

  // This is a static nested class as opposed to an anonymous class assigned to a constant field in
  // order to decouple it's classload when Metadata is loaded. Android users are particularly
  // careful about unnecessary class loading, and we've used similar mechanisms in Guava (see
  // CharMatchers)
  private static final class Empty extends Metadata {
    static final Empty INSTANCE = new Empty();

    @Override
    public int size() {
      return 0;
    }

    @Override
    public MetadataKey<?> getKey(int n) {
      throw new IndexOutOfBoundsException("cannot read from empty metadata");
    }

    @Override
    public Object getValue(int n) {
      throw new IndexOutOfBoundsException("cannot read from empty metadata");
    }

    @Override
    @Nullable
    public <T> T findValue(MetadataKey<T> key) {
      return null;
    }
  }

  /** Returns the number of key/value pairs for this instance. */
  public abstract int size();

  /**
   * Returns the key for the Nth piece of metadata.
   *
   * @throws IndexOutOfBoundsException if either {@code n < 0} or {n >= getCount()}.
   */
  public abstract MetadataKey<?> getKey(int n);

  /**
   * Returns the non-null value for the Nth piece of metadata.
   *
   * @throws IndexOutOfBoundsException if either {@code n < 0} or {n >= getCount()}.
   */
  public abstract Object getValue(int n);

  /**
   * Returns the first value for the given metadata key, or null if it does not exist.
   *
   * @throws NullPointerException if {@code key} is {@code null}.
   */
  @Nullable
  public abstract <T> T findValue(MetadataKey<T> key);
}
