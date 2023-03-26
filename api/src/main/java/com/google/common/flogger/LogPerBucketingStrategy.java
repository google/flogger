/*
 * Copyright (C) 2023 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.checkArgument;
import static com.google.common.flogger.util.Checks.checkNotNull;

import java.util.HashMap;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Provides a strategy for "bucketing" a potentially unbounded set of log aggregation keys used by
 * the {@link LoggingApi#per(T, LogPerBucketingStrategy<T>)} method.
 *
 * <p>When implementing new strategies not provided by this class, it is important to ensure that
 * the {@code apply()} method returns values from a bounded set of instances wherever possible.
 *
 * <p>This is important because the returned values are held persistently for potentially many
 * different log sites. If a different instance is returned each time {@code apply()} is called, a
 * different instance will be held in each log site. This multiplies the amount of memory that is
 * retained indefinitely by any use of {@link LoggingApi#per(T,LogPerBucketingStrategy<T>)}.
 * 
 * <p>One way to handle arbitrary key types would be to create a strategy which "interns" instances
 * in some way, to produce singleton identifiers. Unfortunately interning can itself be a cause of
 * unbounded memory leaks, so a bucketing strategy wishing to perform interning should probably
 * support a user defined maximum capacity to limit the overall risk. If too many instances are
 * seen, the strategy should begin to return {@code null} (and log an appropriate warning).
 * 
 * <p>The additional complexity created by this approach really tells us that types which require
 * interning in order to be used as aggregation keys should be considered unsuitable, and callers
 * should seek alternatives.
 */
public abstract class LogPerBucketingStrategy<T> {
  private static final LogPerBucketingStrategy<Object> KNOWN_BOUNDED =
      new LogPerBucketingStrategy<Object>("KnownBounded") {
        @Override
        protected Object apply(Object key) {
          return key;
        }
      };

  // The is a "safe" strategy as far as memory use is concerned since class objects are effectively
  // singletons.
  private static final LogPerBucketingStrategy<Object> BY_CLASS =
      new LogPerBucketingStrategy<Object>("ByClass") {
        @Override
        protected Object apply(Object key) {
          return key.getClass();
        }
      };

  // The is a "safe" strategy as far as memory use is concerned, because a class object returns the
  // same string instance every time its called, and class objects are effectively singletons.
  private static final LogPerBucketingStrategy<Object> BY_CLASS_NAME =
      new LogPerBucketingStrategy<Object>("ByClassName") {
        @Override
        protected Object apply(Object key) {
          // This is a naturally interned value, so no need to call intern().
          return key.getClass().getName();
        }
      };

  /**
   * A strategy to use only if the set of log aggregation keys is known to be a strictly bounded set
   * of instances with singleton semantics.
   *
   * <p><em>WARNING</em>: When using this strategy, keys passed to {@link
   * LoggingApi#per(T,LogPerBucketingStrategy<T>)} are used as-is by the log aggregation code, and
   * held indefinitely by internal static data structures. As such it is vital that key instances
   * used with this strategy have singleton semantics (i.e. if {@code k1.equals(k2)} then {@code k1
   * == k2}). Failure to adhere to this requirement is likely to result in hard to detect memory
   * leaks.
   *
   * <p>If keys do not have singleton semantics then you should use a different strategy, such as
   * {@link #byHashCode(int)} or {@link #byClass()}.
   */
  public static final LogPerBucketingStrategy<Object> knownBounded() {
    return KNOWN_BOUNDED;
  }

  /**
   * A strategy which uses the {@code Class} of the given key for log aggregation. This is useful
   * when you need to aggregate over specific exceptions or similar type-distinguished instances.
   *
   * <p>Note that using this strategy will result in a reference to the {@code Class} object of the
   * key being retained indefinitely. This will prevent class unloading from occurring for affected
   * classes, and it is up to the caller to decide if this is acceptable or not.
   */
  public static final LogPerBucketingStrategy<Object> byClass() {
    return BY_CLASS;
  }

  /**
   * A strategy which uses the {@code Class} name of the given key for log aggregation. This is
   * useful when you need to aggregate over specific exceptions or similar type-distinguished
   * instances.
   *
   * <p>This is an alternative strategy to {@link #byClass()} which avoids holding onto the class
   * instance and avoids any issues with class unloading. However it may conflate classes if
   * applications use complex arrangements of custom of class-loaders, but this should be extremely
   * rare.
   */
  public static final LogPerBucketingStrategy<Object> byClassName() {
    return BY_CLASS_NAME;
  }

  /**
   * A strategy defined for some given set of known keys.
   *
   * <p>Unlike {@link #knownBounded()}, this strategy maps keys a bounded set of identifiers, and
   * permits the use of non-singleton keys in {@link LoggingApi#per(T,LogPerBucketingStrategy<T>)}.
   *
   * <p>If keys outside this set are used this strategy returns {@code null}, and log aggregation
   * will not occur. Duplicates in {@code knownKeys} are ignored.
   */
  public static final LogPerBucketingStrategy<Object> forKnownKeys(Iterable<?> knownKeys) {
    final HashMap<Object, Integer> keyMap = new HashMap<Object, Integer>();
    StringBuilder name = new StringBuilder("ForKnownKeys(");
    int index = 0;
    for (Object key : knownKeys) {
      checkNotNull(key, "key");
      if (!keyMap.containsKey(key)) {
        name.append(index > 0 ? ", " : "").append(key);
        keyMap.put(key, index++);
      }
    }
    checkArgument(!keyMap.isEmpty(), "knownKeys must not be empty");
    name.append(")");
    return new LogPerBucketingStrategy<Object>(name.toString()) {
      @Override
      @NullableDecl
      protected Object apply(Object key) {
        return keyMap.get(key);
      }
    };
  }

  /**
   * A strategy which uses the {@code hashCode()} of a given key, modulo {@code maxBuckets}, for log
   * aggregation.
   *
   * <p>This is a fallback strategy for cases where the set of possible values is not known in
   * advance, or could be arbirarily large in unusual circumstances.
   * 
   * <p>When using this method it is obviously important that the {@code hashCode()} method of the
   * expected keys is well distributed, since duplicate hash codes, or hash codes congruent to
   * {@code maxBuckets} will cause keys to be conflated.
   *
   * <p>The caller is responsible for deciding the number of unique log aggregation keys this
   * strategy can return. This choice is a trade-off between memory usage and the risk of conflating
   * keys when performing log aggregation. Each log site using this strategy will hold up to {@code
   * maxBuckets} distinct versions of log site information to allow rate limiting and other stateful
   * operations to be applied separately per bucket. The overall allocation cost depends on the type
   * of rate limiting used alongside this method, but it scales linearly with {@code maxBuckets}.
   * 
   * <p>It is recommended to keep the value of {@code maxBuckets} below 250, since this guarantees
   * no additional allocations will occur when using this strategy, however the value chosen should
   * be as small as practically possible for the typical expected number of unique keys.
   *
   * <p>To avoid unwanted allocation at log sites, users are strongly encouraged to assign the
   * returned value to a static field, and pass that to any log statements which need it.
   */
  public static final LogPerBucketingStrategy<Object> byHashCode(final int maxBuckets) {
    checkArgument(maxBuckets > 0, "maxBuckets must be positive");
    return new LogPerBucketingStrategy<Object>("ByHashCode(" + maxBuckets + ")") {
      @Override
      protected Object apply(Object key) {
        // Modulo can return -ve values and we want a value in the range (0 <= modulo < maxBuckets).
        // Note: Math.floorMod() is Java 8, so cannot be used here (yet) otherwise we would just do:
        // return Math.floorMod(key.hashCode(), maxBuckets) - 128;
        int modulo = key.hashCode() % maxBuckets;
        // Can only be -ve if the hashcode was negative, and if so (-maxBuckets < modulo < 0).
        // The following adds maxBuckets if modulo was negative, or zero (saves a branch).
        modulo += (modulo >> 31) & maxBuckets;
        // Subtract 128 from the modulo in order to take full advantage of the promised Integer
        // cache in the JVM (ensuring up to 256 cached values). From java.lang.Integer#valueOf():
        // ""This method will always cache values in the range -128 to 127 ...""
        return modulo - 128;
      }
    };
  }

  private final String name;

  /** Instantiates a strategy with the specified name (used for debugging). */
  protected LogPerBucketingStrategy(String name) {
    this.name = checkNotNull(name, "name");
  }

  /**
   * Maps a log aggregation key from a potentially unbounded set of key values to a bounded set of
   * instances.
   *
   * <p>Implementations of this method should be efficient, and avoid allocating memory wherever
   * possible. The returned value must be an immutable identifier with minimal additional allocation
   * requirements and ideally have singleton semantics (e.g. an {@code Enum} or {@code Integer}
   * value).
   *
   * <p><em>Warning</em>: If keys are not known to have natural singleton semantics (e.g. {@code
   * String}) then returning the given key instance is generally a bad idea. Even if the set of key
   * values is small, the set of distinct allocated instances passed to {@link
   * LoggingApi#per(T,LogPerBucketingStrategy<T>)} can be unbounded, and that's what matters. As
   * such it is always better to map keys to some singleton identifier or intern the keys in some
   * way.
   *
   * @param key a non-null key from a potentially unbounded set of log aggregation keys.
   * @return an immutable value from some known bounded set, which will be held persistently by
   *     internal Flogger data structures as part of the log aggregation feature. If {@code null} is
   *     returned, the corresponding call to {@code per(key, STRATEGY)} has no effect.
   */
  @NullableDecl
  protected abstract Object apply(T key);

  @Override
  public final String toString() {
    return LogPerBucketingStrategy.class.getSimpleName() + "[" + name + "]";
  }
}
