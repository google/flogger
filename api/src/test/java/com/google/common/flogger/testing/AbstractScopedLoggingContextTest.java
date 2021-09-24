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

import static com.google.common.flogger.LogContext.Key.TAGS;
import static com.google.common.flogger.testing.MetadataSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.LoggingScope;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopeType;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import com.google.common.flogger.context.ScopedLoggingContexts;
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

  private static final ScopeType SUB_TASK = ScopeType.create("sub task");
  private static final ScopeType BATCH_JOB = ScopeType.create("batch job");

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
  private void checkDone() {
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
  public void testNewContext_withTags() {
    assertThat(getTagMap()).isEmpty();
    context
        .newContext()
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

  // Note that general Metadata isn't merged automatically in the same way as Tags at the moment,
  // so there's no equivalent test for it.
  @Test
  public void testLoggedTags_areMerged() {
    FakeLoggerBackend backend = new FakeLoggerBackend();
    TestLogger logger = TestLogger.create(backend);

    Tags logSiteTags = Tags.of("foo", "bar");
    // We need to add tags manually inside a context to check if there is a "real" context data
    // provider installed. We can't use getImplementationUnderTest() here sadly since these APIs
    // go via the Platform class, which uses the installed provider, which can differ from what's
    // returned by getImplementationUnderTest(). This code only needs to be tested by one "real"
    // implementation to get coverage however as merging tags is not done by the data provider.
    boolean canAddTags;
    try (LoggingContextCloseable ctx = ScopedLoggingContexts.newContext().install()) {
      canAddTags = ScopedLoggingContexts.addTags(Tags.of("foo", "baz"));
      logger.atInfo().with(TAGS, logSiteTags).log("With tags");
    }

    // Merged tag values are ordered based on the values, not the order in which that are added.
    Tags expected =
        canAddTags ? Tags.builder().addTag("foo", "bar").addTag("foo", "baz").build() : logSiteTags;
    assertThat(backend.getLoggedCount()).isEqualTo(1);
    backend.assertLogged(0).metadata().containsUniqueEntry(TAGS, expected);
  }

  @Test
  public void testNewContext_withMetadata() {
    assertThat(getMetadata()).hasSize(0);
    context
        .newContext()
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
  public void testNewContext_withLogLevelMap() {
    assertLogging("foo.bar.Bar", Level.FINE).isFalse();
    LogLevelMap levelMap = LogLevelMap.create(ImmutableMap.of("foo.bar", Level.FINE), Level.FINE);
    context
        .newContext()
        .withLogLevelMap(levelMap)
        .run(
            () -> {
              assertLogging("foo.bar.Bar", Level.FINE).isTrue();
              markTestAsDone();
            });
    assertLogging("foo.bar.Bar", Level.FINE).isFalse();
    checkDone();
  }

  @Test
  public void testNewContext_withMergedTags() {
    assertThat(getTagMap()).isEmpty();
    context
        .newContext()
        .withTags(Tags.of("foo", "bar"))
        .run(
            () -> {
              assertThat(getTagMap()).hasSize(1);
              assertThat(getTagMap().get("foo")).containsExactly("bar");
              context
                  .newContext()
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
  public void testNewContext_withConcatenatedMetadata() {
    assertThat(getMetadata()).hasSize(0);
    context
        .newContext()
        .withMetadata(FOO_KEY, "first")
        .withMetadata(BAR_KEY, "one")
        .run(
            () -> {
              assertThat(getMetadata()).hasSize(2);
              assertThat(getMetadata()).containsEntries(FOO_KEY, "first");
              assertThat(getMetadata().findValue(FOO_KEY)).isEqualTo("first");
              assertThat(getMetadata()).containsEntries(BAR_KEY, "one");
              context
                  .newContext()
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
  public void testNewContext_withMergedLevelMap() {
    assertLogging("other.package", Level.FINE).isFalse();
    assertLogging("foo.bar", Level.FINE).isFalse();
    assertLogging("foo.bar.Baz", Level.FINE).isFalse();
    // Everything in "foo.bar" gets at least FINE logging.
    LogLevelMap fooBarFine = LogLevelMap.create(ImmutableMap.of("foo.bar", Level.FINE), Level.INFO);
    context
        .newContext()
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
                  .newContext()
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

  @Test
  public void testNewContext_withBoundScopeTypes() {
    assertThat(dataProvider.getScope(SUB_TASK)).isNull();
    assertThat(dataProvider.getScope(BATCH_JOB)).isNull();
    context
        .newContext(SUB_TASK)
        .run(
            () -> {
              LoggingScope taskScope = dataProvider.getScope(SUB_TASK);
              assertThat(taskScope).isNotNull();
              assertThat(taskScope.toString()).isEqualTo("sub task");
              assertThat(dataProvider.getScope(BATCH_JOB)).isNull();
              context
                  .newContext(BATCH_JOB)
                  .run(
                      () -> {
                        assertThat(dataProvider.getScope(SUB_TASK)).isSameInstanceAs(taskScope);
                        assertThat(dataProvider.getScope(BATCH_JOB)).isNotNull();
                        markTestAsDone();
                      });
              // Everything is restored after a scope.
              assertThat(dataProvider.getScope(SUB_TASK)).isSameInstanceAs(taskScope);
              assertThat(dataProvider.getScope(BATCH_JOB)).isNull();
            });
    // Everything is restored after a scope.
    assertThat(dataProvider.getScope(SUB_TASK)).isNull();
    assertThat(dataProvider.getScope(BATCH_JOB)).isNull();
    checkDone();
  }

  @Test
  public void testNewContext_repeatedScopesAreIdempotent() {
    assertThat(dataProvider.getScope(SUB_TASK)).isNull();
    context
        .newContext(SUB_TASK)
        .run(
            () -> {
              LoggingScope taskScope = dataProvider.getScope(SUB_TASK);
              assertThat(taskScope).isNotNull();
              assertThat(taskScope.toString()).isEqualTo("sub task");
              context
                  .newContext(SUB_TASK)
                  .run(
                      () -> {
                        // We don't make a new scope instance if the same type is bound twice!
                        assertThat(dataProvider.getScope(SUB_TASK)).isSameInstanceAs(taskScope);
                        markTestAsDone();
                      });
              assertThat(dataProvider.getScope(SUB_TASK)).isNotNull();
            });
    assertThat(dataProvider.getScope(SUB_TASK)).isNull();
    checkDone();
  }
}
