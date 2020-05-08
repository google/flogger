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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.Metadata;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A <a href="https://github.com/google/truth">Truth</a> subject for {@link Metadata}. */
public final class MetadataSubject extends Subject {
  private static final Subject.Factory<MetadataSubject, Metadata> METADATA_SUBJECT_FACTORY =
      MetadataSubject::new;

  public static Subject.Factory<MetadataSubject, Metadata> metadata() {
    return METADATA_SUBJECT_FACTORY;
  }

  public static MetadataSubject assertThat(@NullableDecl Metadata metadata) {
    return assertAbout(metadata()).that(metadata);
  }

  private final Metadata actual;

  private MetadataSubject(FailureMetadata failureMetadata, @NullableDecl Metadata subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  private List<MetadataKey<?>> keyList() {
    Metadata metadata = actual;
    List<MetadataKey<?>> keys = new ArrayList<>();
    for (int n = 0; n < metadata.size(); n++) {
      keys.add(metadata.getKey(n));
    }
    return keys;
  }

  private List<Object> valueList() {
    Metadata metadata = actual;
    List<Object> values = new ArrayList<>();
    for (int n = 0; n < metadata.size(); n++) {
      values.add(metadata.getValue(n));
    }
    return values;
  }

  private <T> List<T> valuesOf(MetadataKey<T> key) {
    Metadata metadata = actual;
    List<T> values = new ArrayList<>();
    for (int n = 0; n < metadata.size(); n++) {
      if (metadata.getKey(n).equals(key)) {
        values.add(key.cast(metadata.getValue(n)));
      }
    }
    return values;
  }

  public void hasSize(int expectedSize) {
    checkArgument(expectedSize >= 0, "expectedSize(%s) must be >= 0", expectedSize);
    check("size()").that(actual.size()).isEqualTo(expectedSize);
  }

  public <T> void containsUniqueEntry(MetadataKey<T> key, T value) {
    checkNotNull(key, "key must not be null");
    checkNotNull(value, "value must not be null");
    T actual = this.actual.findValue(key);
    if (actual == null) {
      failWithActual("expected to contain value for key", key);
    } else {
      check("findValue(%s)", key).that(actual).isEqualTo(value);
      // The key must exist, so neither method will return -1.
      List<MetadataKey<?>> keys = keyList();
      if (keys.indexOf(key) != keys.lastIndexOf(key)) {
        failWithActual("expected to have unique key", key);
      }
    }
  }

  public <T> void containsEntries(MetadataKey<T> key, T... values) {
    checkNotNull(key, "key must not be null");
    check("<values of>(%s)", key).that(valuesOf(key)).containsExactlyElementsIn(values).inOrder();
  }

  public IterableSubject keys() {
    return check("keys()").that(keyList());
  }

  public IterableSubject values() {
    return check("values()").that(valueList());
  }
}

