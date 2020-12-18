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

package com.google.common.flogger;

import static com.google.common.flogger.util.Checks.checkNotNull;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Used by Scope/LogSiteMap and in response to "per()" or "perUnique()" (which is an implicitly
 * unbounded scope. This should avoid it needing to be made public assuming it's in the same
 * package.
 */
final class SpecializedLogSiteKey implements LogSiteKey {
  static LogSiteKey of(LogSiteKey key, Object qualifier) {
    return new SpecializedLogSiteKey(key, qualifier);
  }

  private final LogSiteKey delegate;
  private final Object qualifier;

  private SpecializedLogSiteKey(LogSiteKey key, Object qualifier) {
    this.delegate = checkNotNull(key, "log site key");
    this.qualifier = checkNotNull(qualifier, "log site qualifier");
  }

  // Equals is dependent on the order in which specialization occurred, even though conceptually it
  // needn't be.
  @Override
  public boolean equals(@NullableDecl Object obj) {
    if (!(obj instanceof SpecializedLogSiteKey)) {
      return false;
    }
    SpecializedLogSiteKey other = (SpecializedLogSiteKey) obj;
    return delegate.equals(other.delegate) && qualifier.equals(other.qualifier);
  }

  @Override
  public int hashCode() {
    // Use XOR (which is symmetric) so hash codes are not dependent on specialization order.
    return delegate.hashCode() ^ qualifier.hashCode();
  }

  @Override
  public String toString() {
    return "SpecializedLogSiteKey{ delegate='" + delegate + "', qualifier='" + qualifier + "' }";
  }
}
