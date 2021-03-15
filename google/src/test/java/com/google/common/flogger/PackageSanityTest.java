/*
 * Copyright (C) 2015 The Flogger Authors.
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

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.testing.FakeLoggerBackend;
import com.google.common.flogger.testing.FakeMetadata;
import com.google.common.testing.AbstractPackageSanityTests;

/**
 * Covers basic sanity checks for the entire package.
 *
 * @author Kurt Alfred Kluever
 */
@SuppressWarnings("BetaApi") // this is a test, so we can control the version of guava-testlib
public class PackageSanityTest extends AbstractPackageSanityTests {
  // Classes which must be ignored (they handle their own nullness check). We _cannot_ use the
  // trick of adding a testNulls() method in the tests of classes in other package paths, since
  // they may not be built.
  private static final ImmutableSet<Class<?>> IGNORE_CLASSES =
      ImmutableSet.of(MetadataKey.class);

  public PackageSanityTest() {
    // This works around the issue that StackTraceElement has _no_ public constructor, but we need
    // to create them for some of the null pointer sanity checks. Any 2 distinct values will do.
    StackTraceElement[] stack = new NullPointerException().getStackTrace();
    setDistinctValues(StackTraceElement.class, stack[0], stack[1]);
    setDefault(LoggerBackend.class, new FakeLoggerBackend("com.example.NullTester"));
    setDefault(MetadataKey.class, new MetadataKey<>("dummy", String.class, false));
    setDefault(Metadata.class, new FakeMetadata());
    ignoreClasses(Predicates.in(IGNORE_CLASSES));
  }
}
