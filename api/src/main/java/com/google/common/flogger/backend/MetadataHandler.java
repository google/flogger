/*
 * Copyright (C) 2020 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.checkArgument;
import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.MetadataKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Callback API for logger backend implementations to handle metadata keys/values. The API methods
 * will be called once for each distinct key, in encounter order. Different methods are called
 * depending on whether the key is repeatable or not.
 *
 * <p>It is expected that the most convenient way to construct a metadata handler is via the {@link
 * MetadataHandler.Builder Builder} class, which lets keys be individually mapped to callbacks,
 * however the class can also just be extended to implement alternate/custom behavior.
 *
 * @param <C> the arbitrary context type.
 */
public abstract class MetadataHandler<C> {
  /**
   * Handles a single metadata key/value mapping. This method is called directly for singleton non
   * repeatable) keys, but may also be called for repeated keys by the default implementation of
   * {@link #handleRepeated}. It is up to the implementation to override that method if this
   * behaviour is unwanted.
   *
   * @param key the metadata key (not necessarily a "singleton" key).
   * @param value associated metadata value.
   * @param context an arbitrary context object supplied to the process method.
   * @param <T> the value type.
   */
  protected abstract <T> void handle(MetadataKey<T> key, T value, C context);

  /**
   * Handles values for a repeatable metadata key. The method is called for all repeatable keys
   * (even those with only one value). The default implementation makes repeated callbacks to the
   * {@link #handle} method, in order, for each value.
   *
   * @param key the repeatable metadata key.
   * @param values a lightweight iterator over all values associated with the key. Note that this
   *     instance is read-only and must not be held beyond the scope of this callback.
   * @param context an arbitrary context object supplied to the process method.
   * @param <T> the value type.
   */
  protected <T> void handleRepeated(MetadataKey<T> key, Iterator<T> values, C context) {
    while (values.hasNext()) {
      handle(key, values.next(), context);
    }
  }

  /**
   * Returns a builder for a handler with the specified default callback. The default handler will
   * receive all key/value pairs from the metadata individually, which can result in repeated keys
   * being seen more than once.
   *
   * <p>A default handler is required because no handler can know the complete set of keys which
   * might be available and it is very undesirable to drop unknown keys. If default repeated values
   * should be handled together, then {@link
   * Builder#setDefaultRepeatedHandler(RepeatedValueHandler)} should be called as well.
   *
   * <p>Unknown keys/values can only be handled in a generic fashion unless a given key is matched
   * to a known constant. However the entire point of this map-based handler is to avoid any need to
   * do explicit matching, so the default handler should not need to know the value type.
   *
   * @param defaultHandler the default handler for unknown keys/values.
   * @param <C> the context type.
   */
  public static <C> Builder<C> builder(ValueHandler<Object, C> defaultHandler) {
    return new Builder<C>(defaultHandler);
  }

  /**
   * API for handling metadata key/value pairs individually.
   *
   * @param <T> the value type.
   * @param <C> the type of the context passed to the callbacks.
   */
  public interface ValueHandler<T, C> {
    /**
     * Handles metadata values individually.
     *
     * @param key the metadata key (not necessarily a "singleton" key).
     * @param value associated metadata value.
     * @param context an arbitrary context object supplied to the process method.
     */
    void handle(MetadataKey<? extends T> key, T value, C context);
  }

  /**
   * API for handling repeated metadata key/values in a single callback.
   *
   * @param <T> the value type.
   * @param <C> the type of the context passed to the callbacks.
   */
  public interface RepeatedValueHandler<T, C> {
    /**
     * Handles all repeated metadata values for a given key.
     *
     * @param key the repeatable metadata key for which this handler was registered, or an unknown
     *     key if this is the default handler.
     * @param values a lightweight iterator over all values associated with the key. Note that this
     *     instance is read-only and must not be held beyond the scope of this callback.
     * @param context an arbitrary context object supplied to the process method.
     */
    void handle(MetadataKey<? extends T> key, Iterator<T> values, C context);
  }

  /**
   * Builder for a map-based {@link MetadataHandler} which allows handlers to be associated with
   * individual callbacks.
   *
   * @param <C> the context type.
   */
  public static final class Builder<C> {
    private final Map<MetadataKey<?>, ValueHandler<?, C>> singleValueHandlers =
        new HashMap<MetadataKey<?>, ValueHandler<?, C>>();
    private final Map<MetadataKey<?>, RepeatedValueHandler<?, C>> repeatedValueHandlers =
        new HashMap<MetadataKey<?>, RepeatedValueHandler<?, C>>();
    private final ValueHandler<Object, C> defaultHandler;
    private RepeatedValueHandler<Object, C> defaultRepeatedHandler = null;

    private Builder(ValueHandler<Object, C> defaultHandler) {
      this.defaultHandler = checkNotNull(defaultHandler, "default handler");
    }

    /**
     * Sets a handler for any unknown repeated keys which allows values to be processed via a
     * generic {@link Iterator<Object>}. To handle repeated values against a known key with their
     * expected type, register a handler via {@link #addRepeatedHandler(MetadataKey,
     * RepeatedValueHandler)}.
     *
     * <p>Note that if a repeated key is associated with an individual value handler (i.e. via
     * {@link #addHandler(MetadataKey, ValueHandler)}), then that will be used in preference to the
     * default handler set here.
     *
     * @param defaultHandler the default handler for unknown repeated keys/values.
     * @return the builder instance for chaining.
     */
    public Builder<C> setDefaultRepeatedHandler(RepeatedValueHandler<Object, C> defaultHandler) {
      this.defaultRepeatedHandler = checkNotNull(defaultHandler, "handler");
      return this;
    }

    /**
     * Registers a value handler for the specified key, replacing any previously registered value.
     *
     * @param key the key for which the handler should be invoked (can be a repeated key).
     * @param handler the value handler to be invoked for every value associated with the key.
     * @param <T> the value type.
     * @return the builder instance for chaining.
     */
    public <T> Builder<C> addHandler(MetadataKey<T> key, ValueHandler<T, C> handler) {
      checkNotNull(key, "key");
      checkNotNull(handler, "handler");
      repeatedValueHandlers.remove(key);
      singleValueHandlers.put(key, handler);
      return this;
    }

    /**
     * Registers a repeated value handler for the specified key, replacing any previously registered
     * value.
     *
     * @param key the repeated key for which the handler should be invoked.
     * @param handler the repeated value handler to be invoked once for all associated values.
     * @param <T> the value type.
     * @return the builder instance for chaining.
     */
    public <T> Builder<C> addRepeatedHandler(
        MetadataKey<T> key, RepeatedValueHandler<T, C> handler) {
      checkNotNull(key, "key");
      checkNotNull(handler, "handler");
      checkArgument(key.canRepeat(), "key must be repeating");
      singleValueHandlers.remove(key);
      repeatedValueHandlers.put(key, handler);
      return this;
    }

    /**
     * Removes any existing handlers for the given keys. This method is useful when making several
     * handlers with different mappings from a single builder.
     *
     * @param keys the set of keys to remove from the builder.
     * @return the builder instance for chaining.
     */
    public Builder<C> removeHandlers(MetadataKey<?>... keys) {
      for (MetadataKey<?> key : keys) {
        checkNotNull(key, "key");
        singleValueHandlers.remove(key);
        repeatedValueHandlers.remove(key);
      }
      return this;
    }

    /** Returns the immutable, map-based metadata handler. */
    public MetadataHandler<C> build() {
      return new MapBasedhandler<C>(this);
    }
  }

  private static final class MapBasedhandler<C> extends MetadataHandler<C> {
    private final Map<MetadataKey<?>, ValueHandler<?, C>> singleValueHandlers =
        new HashMap<MetadataKey<?>, ValueHandler<?, C>>();
    private final Map<MetadataKey<?>, RepeatedValueHandler<?, C>> repeatedValueHandlers =
        new HashMap<MetadataKey<?>, RepeatedValueHandler<?, C>>();
    private final ValueHandler<Object, C> defaultHandler;
    private final RepeatedValueHandler<Object, C> defaultRepeatedHandler;

    private MapBasedhandler(Builder<C> builder) {
      this.singleValueHandlers.putAll(builder.singleValueHandlers);
      this.repeatedValueHandlers.putAll(builder.repeatedValueHandlers);
      this.defaultHandler = builder.defaultHandler;
      this.defaultRepeatedHandler = builder.defaultRepeatedHandler;
    }

    @Override
    protected <T> void handle(MetadataKey<T> key, T value, C context) {
      @SuppressWarnings("unchecked") // Safe because of how our private map is managed.
      ValueHandler<T, C> handler = (ValueHandler<T, C>) singleValueHandlers.get(key);
      if (handler != null) {
        handler.handle(key, value, context);
      } else {
        defaultHandler.handle(key, value, context);
      }
    }

    @Override
    protected <T> void handleRepeated(MetadataKey<T> key, Iterator<T> values, C context) {
      @SuppressWarnings("unchecked") // Safe because of how our private map is managed.
      RepeatedValueHandler<T, C> handler =
          (RepeatedValueHandler<T, C>) repeatedValueHandlers.get(key);
      if (handler != null) {
        handler.handle(key, values, context);
      } else if (defaultRepeatedHandler != null && !singleValueHandlers.containsKey(key)) {
        // Safe because the iterator is unmodifiable (and only produces elements of type T).
        @SuppressWarnings("unchecked")
        Iterator<Object> it = (Iterator<Object>) values;
        defaultRepeatedHandler.handle(key, it, context);
      } else {
        // Dispatches keys individually.
        super.handleRepeated(key, values, context);
      }
    }
  }
}
