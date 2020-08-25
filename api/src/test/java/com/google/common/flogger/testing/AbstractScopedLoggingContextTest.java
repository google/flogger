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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.Tags;
import com.google.common.truth.BooleanSubject;
import java.util.logging.Level;
import org.junit.Before;
import org.junit.Test;

/**
 * A helper class to allow implementations of {@link ContextDataProvider} to be tested against a
 * suite of common tests.
 */
public abstract class AbstractScopedLoggingContextTest {

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

  @Test
  public void testNewScope_withTags() {
    assertThat(dataProvider.getTags().asMap()).isEmpty();
    context.newScope().withTags(Tags.of("foo", "bar")).run(() -> {
      assertThat(dataProvider.getTags().asMap()).containsExactly("foo", ImmutableSet.of("bar"));
      markTestAsDone();
    });
    assertThat(dataProvider.getTags().asMap()).isEmpty();
    checkDone();
  }

  @Test
  public void testNewScope_withLogLevelMap() {
    assertThat(dataProvider.shouldForceLogging("foo.bar.Bar", Level.FINE, false)).isFalse();
    LogLevelMap levelMap = LogLevelMap.create(ImmutableMap.of("foo.bar", Level.FINE), Level.FINE);
    context.newScope().withLogLevelMap(levelMap).run(() -> {
      assertThat(dataProvider.shouldForceLogging("foo.bar.Bar", Level.FINE, false)).isTrue();
      markTestAsDone();
    });
    assertThat(dataProvider.shouldForceLogging("foo.bar.Bar", Level.FINE, false)).isFalse();
    checkDone();
  }

  @Test
  public void testNewScopes_withMergedTags() {
    assertNoTags();
    context
        .newScope()
        .withTags(Tags.of("foo", "bar"))
        .run(
            () -> {
              assertTagValues("foo", "bar");
              context
                  .newScope()
                  .withTags(Tags.of("foo", "baz"))
                  .run(
                      () -> {
                        assertTagValues("foo", "bar", "baz");
                        markTestAsDone();
                      });
              // Everything is restored after a scope.
              assertTagValues("foo", "bar");
            });
    // Everything is restored after a scope.
    assertNoTags();
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

  private void assertNoTags() {
    assertThat(dataProvider.getTags().asMap()).isEmpty();
  }

  private void assertTagValues(String key, String... values) {
    assertThat(dataProvider.getTags().asMap()).containsEntry(key, ImmutableSet.copyOf(values));
  }

  private BooleanSubject assertLogging(String name, Level level) {
    return assertWithMessage("shouldForceLogging(\"%s\", %s, false)", name, level)
        .that(dataProvider.shouldForceLogging(name, level, false));
  }
}
