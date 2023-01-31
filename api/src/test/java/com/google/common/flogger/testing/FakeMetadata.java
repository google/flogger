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

package com.google.common.flogger.testing;

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.Metadata;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A mutable fake {@link Metadata} implementation to help test logging backends and other log
 * handling code.
 */
public final class FakeMetadata extends Metadata {

  private static final class KeyValuePair<T> {
    private final MetadataKey<T> key;
    private final T value;
    private KeyValuePair(MetadataKey<T> key, T value) {
      this.key = checkNotNull(key, "key");
      this.value = checkNotNull(value, "value");
    }
  }

  private final List<KeyValuePair<?>> entries = new ArrayList<>();

  @CanIgnoreReturnValue
  public <T> FakeMetadata add(MetadataKey<T> key, T value) {
    entries.add(new KeyValuePair<T>(key, value));
    return this;
  }

  @Override public int size() {
    return entries.size();
  }

  @Override public MetadataKey<?> getKey(int n) {
    return entries.get(n).key;
  }
  @Override public Object getValue(int n) {
    return entries.get(n).value;
  }

  @Override
  @NullableDecl
  public <T> T findValue(MetadataKey<T> key) {
    for (KeyValuePair<?> e : entries) {
      if (e.key.equals(key)) {
        return key.cast(e.value);
      }
    }
    return null;
  }
}

