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

import static com.google.common.flogger.LogContext.Key.LOG_SITE_GROUPING_KEY;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.LoggingScope.WeakScope;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.testing.FakeLogSite;
import com.google.common.flogger.testing.FakeMetadata;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogSiteMapTest {

  @Test
  public void testGetStatsForKey() {
    LogSiteMap<AtomicInteger> map = new LogSiteMap<AtomicInteger>() {
      @Override
      protected AtomicInteger initialValue() {
        return new AtomicInteger();
      }
    };

    LogSite logSite1 = FakeLogSite.create("class1", "method1", 1, "path1");
    LogSite logSite2 = FakeLogSite.create("class2", "method2", 2, "path2");

    AtomicInteger stats1 = map.get(logSite1, Metadata.empty());
    AtomicInteger stats2 = map.get(logSite2, Metadata.empty());

    assertThat(stats1).isNotNull();
    assertThat(stats2).isNotNull();
    assertThat(stats2).isNotSameInstanceAs(stats1);
    assertThat(map.get(logSite1, Metadata.empty())).isSameInstanceAs(stats1);
    assertThat(map.get(logSite2, Metadata.empty())).isSameInstanceAs(stats2);
  }

  @Test
  public void testManuallyClosingScopesRemovesEntries() {
    LogSiteMap<AtomicInteger> map =
        new LogSiteMap<AtomicInteger>() {
          @Override
          protected AtomicInteger initialValue() {
            return new AtomicInteger(0);
          }
        };

    WeakScope foo = new WeakScope("foo");
    FakeMetadata fooMetadata = new FakeMetadata().add(LOG_SITE_GROUPING_KEY, foo);

    WeakScope bar = new WeakScope("bar");
    FakeMetadata barMetadata = new FakeMetadata().add(LOG_SITE_GROUPING_KEY, bar);

    LogSite logSite = FakeLogSite.create("com.google.foo.Foo", "doFoo", 42, "<unused>");
    LogSiteKey fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata);
    LogSiteKey barKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, barMetadata);

    assertThat(map.get(fooKey, fooMetadata).incrementAndGet()).isEqualTo(1);
    // Same metadata, non-specialized key (scope is also not in the metadata).
    assertThat(map.get(logSite, FakeMetadata.empty()).incrementAndGet()).isEqualTo(1);
    // Same metadata, specialized key (2nd time).
    assertThat(map.get(fooKey, fooMetadata).incrementAndGet()).isEqualTo(2);
    // Different metadata, new specialized key.
    assertThat(map.get(barKey, barMetadata).incrementAndGet()).isEqualTo(1);

    assertThat(map.contains(logSite)).isTrue();
    assertThat(map.contains(fooKey)).isTrue();
    assertThat(map.contains(barKey)).isTrue();

    foo.closeForTesting();
    assertThat(map.contains(logSite)).isTrue();
    assertThat(map.contains(fooKey)).isFalse();
    assertThat(map.contains(barKey)).isTrue();
  }

  @Test
  public void testEntriesAreRemovedWhenScopesAreGarbageCollected() throws Exception {
    LogSiteMap<AtomicInteger> map =
        new LogSiteMap<AtomicInteger>() {
          @Override
          protected AtomicInteger initialValue() {
            return new AtomicInteger(0);
          }
        };

    // We hope the scope of the returned key can be GC'd once the recursion is over.
    LogSiteKey fooKey = recurseAndCall(10, () -> useAndReturnScopedKey(map, "foo"));

    // GC should collect the Scope reference used in the recursive call.
    System.gc();
    Thread.sleep(1000);
    System.gc();

    // Adding new keys in a different scope triggers tidying up of keys from unreachable scopes.
    assertThat(map.contains(useAndReturnScopedKey(map, "bar"))).isTrue();

    // This is what's being tested! The scope becoming unreachable causes old keys to be removed.
    assertThat(map.contains(fooKey)).isFalse();
  }

  private static LogSiteKey useAndReturnScopedKey(LogSiteMap<AtomicInteger> map, String label) {
    LoggingScope scope = LoggingScope.create(label);
    FakeMetadata metadata = new FakeMetadata().add(LOG_SITE_GROUPING_KEY, scope);
    LogSite logSite = FakeLogSite.create("com.example", label, 42, "<unused>");
    LogSiteKey key = LogContext.specializeLogSiteKeyFromMetadata(logSite, metadata);
    map.get(key, metadata).incrementAndGet();
    assertThat(map.contains(key)).isTrue();
    return key;
  }

  private static <T> T recurseAndCall(int n, Callable<T> r) throws Exception {
    return (n-- <= 0) ? r.call() : recurseAndCall(n, r);
  }
}
