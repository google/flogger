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

package com.google.common.flogger.testing;

import static com.google.common.flogger.testing.MetadataSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.Tags;
import com.google.common.truth.BooleanSubject;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.junit.Before;
import org.junit.Test;

/**
 * A JUnit4 compatible helper class to allow implementations of {@link ContextDataProvider} to be
 * tested against a suite of common tests.
 */
public abstract class AbstractScopedLoggingContextTest {
  private static final MetadataKey<String> FOO_KEY = MetadataKey.single("FOO", String.class);
  private static final MetadataKey<String> BAR_KEY = MetadataKey.repeated("BAR", String.class);

  protected abstract ContextDataProvider getImplementationUnderTest();

  private ContextDataProvider dataProvider;
  private ScopedLoggingContext context;
  // Flag set inside innermost callbacks to prove they were executed.
  private boolean testWasDone = false;

  @Before
  public final void setImplementation() {
    this.dataProvider = getImplementationUnderTest();
    this.context = dataProvider.getContextApiSingleton();
  }

  // Don't use @After here since the subclass may not use this, just put it at the end of all tests.
  private final void checkDone() {
    // If this fails, then the code to set it in the test didn't happen.
    assertThat(testWasDone).isTrue();
  }

  private final void markTestAsDone() {
    this.testWasDone = true;
  }

  private Map<String, Set<Object>> getTagMap() {
    return dataProvider.getTags().asMap();
  }

  private Metadata getMetadata() {
    return dataProvider.getMetadata();
  }

  private BooleanSubject assertLogging(String name, Level level) {
    return assertWithMessage("shouldForceLogging(\"%s\", %s, false)", name, level)
        .that(dataProvider.shouldForceLogging(name, level, false));
  }

  @Test
  public void testNewScope_withTags() {
    assertThat(getTagMap()).isEmpty();
    context
        .newScope()
        .withTags(Tags.of("foo", "bar"))
        .run(
            () -> {
              assertThat(getTagMap()).hasSize(1);
              assertThat(getTagMap().get("foo")).containsExactly("bar");
              markTestAsDone();
            });
    assertThat(getTagMap()).isEmpty();
    checkDone();
  }

  @Test
  public void testNewScope_withMetadata() {
    assertThat(getMetadata()).hasSize(0);
    context
        .newScope()
        .withMetadata(FOO_KEY, "foo")
        .run(
            () -> {
              assertThat(getMetadata()).containsEntries(FOO_KEY, "foo");
              assertThat(getMetadata().findValue(FOO_KEY)).isEqualTo("foo");
              markTestAsDone();
            });
    assertThat(getMetadata()).hasSize(0);
    checkDone();
  }

  @Test
  public void testNewScope_withLogLevelMap() {
    assertThat(dataProvider.shouldForceLogging("foo.bar.Bar", Level.FINE, false)).isFalse();
    LogLevelMap levelMap = LogLevelMap.create(ImmutableMap.of("foo.bar", Level.FINE), Level.FINE);
    context
        .newScope()
        .withLogLevelMap(levelMap)
        .run(
            () -> {
              assertThat(dataProvider.shouldForceLogging("foo.bar.Bar", Level.FINE, false))
                  .isTrue();
              markTestAsDone();
            });
    assertThat(dataProvider.shouldForceLogging("foo.bar.Bar", Level.FINE, false)).isFalse();
    checkDone();
  }

  @Test
  public void testNewScopes_withMergedTags() {
    assertThat(getTagMap()).isEmpty();
    context
        .newScope()
        .withTags(Tags.of("foo", "bar"))
        .run(
            () -> {
              assertThat(getTagMap()).hasSize(1);
              assertThat(getTagMap().get("foo")).containsExactly("bar");
              context
                  .newScope()
                  .withTags(Tags.of("foo", "baz"))
                  .run(
                      () -> {
                        assertThat(getTagMap()).hasSize(1);
                        assertThat(getTagMap().get("foo")).containsExactly("bar", "baz").inOrder();
                        markTestAsDone();
                      });
              // Everything is restored after a scope.
              assertThat(getTagMap()).hasSize(1);
              assertThat(getTagMap().get("foo")).containsExactly("bar");
            });
    // Everything is restored after a scope.
    assertThat(getTagMap()).isEmpty();
    checkDone();
  }

  @Test
  public void testNewScopes_withConcatenatedMetadata() {
    assertThat(getMetadata()).hasSize(0);
    context
        .newScope()
        .withMetadata(FOO_KEY, "first")
        .withMetadata(BAR_KEY, "one")
        .run(
            () -> {
              assertThat(getMetadata()).hasSize(2);
              assertThat(getMetadata()).containsEntries(FOO_KEY, "first");
              assertThat(getMetadata().findValue(FOO_KEY)).isEqualTo("first");
              assertThat(getMetadata()).containsEntries(BAR_KEY, "one");
              context
                  .newScope()
                  .withMetadata(FOO_KEY, "second")
                  .withMetadata(BAR_KEY, "two")
                  .run(
                      () -> {
                        // Enumerating entries allows single-values keys to appear multiple times
                        // (because merging is expensive and it's handled by MetadataProcessor
                        // anyway).
                        assertThat(getMetadata()).hasSize(4);
                        assertThat(getMetadata()).containsEntries(FOO_KEY, "first", "second");
                        assertThat(getMetadata().findValue(FOO_KEY)).isEqualTo("second");
                        assertThat(getMetadata()).containsEntries(BAR_KEY, "one", "two");
                        markTestAsDone();
                      });
              // Everything is restored after a scope.
              assertThat(getMetadata()).hasSize(2);
              assertThat(getMetadata()).containsEntries(FOO_KEY, "first");
              assertThat(getMetadata().findValue(FOO_KEY)).isEqualTo("first");
              assertThat(getMetadata()).containsEntries(BAR_KEY, "one");
            });
    // Everything is restored after a scope.
    assertThat(getMetadata()).hasSize(0);
    checkDone();
  }

  @Test
  public void testNewScopes_withMergedLevelMap() {
    assertLogging("other.package", Level.FINE).isFalse();
    assertLogging("foo.bar", Level.FINE).isFalse();
    assertLogging("foo.bar.Baz", Level.FINE).isFalse();
    // Everything in "foo.bar" gets at least FINE logging.
    LogLevelMap fooBarFine = LogLevelMap.create(ImmutableMap.of("foo.bar", Level.FINE), Level.INFO);
    context
        .newScope()
        .withLogLevelMap(fooBarFine)
        .run(
            () -> {
              assertLogging("other.package", Level.FINE).isFalse();
              assertLogging("foo.bar", Level.FINE).isTrue();
              assertLogging("foo.bar.Baz", Level.FINE).isTrue();
              assertLogging("foo.bar.Baz", Level.FINEST).isFalse();
              // Everything in "foo.bar.Baz" *additionally* gets FINEST logging.
              LogLevelMap bazFinest =
                  LogLevelMap.create(ImmutableMap.of("foo.bar.Baz", Level.FINEST), Level.INFO);
              context
                  .newScope()
                  .withLogLevelMap(bazFinest)
                  .run(
                      () -> {
                        assertLogging("other.package", Level.FINE).isFalse();
                        assertLogging("foo.bar", Level.FINE).isTrue();
                        assertLogging("foo.bar.Baz", Level.FINEST).isTrue();
                        markTestAsDone();
                      });
              // Everything is restored after a scope.
              assertLogging("other.package", Level.FINE).isFalse();
              assertLogging("foo.bar", Level.FINE).isTrue();
              assertLogging("foo.bar.Baz", Level.FINE).isTrue();
              assertLogging("foo.bar.Baz", Level.FINEST).isFalse();
            });
    // Everything is restored after a scope.
    assertLogging("other.package", Level.FINE).isFalse();
    assertLogging("foo.bar", Level.FINE).isFalse();
    assertLogging("foo.bar.Baz", Level.FINE).isFalse();
    checkDone();
  }
}
