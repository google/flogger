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

import com.google.common.flogger.backend.Metadata;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An opaque scope marker which can be attached to log sites to provide "per scope" behaviour for
 * stateful logging operations (e.g. rate limiting).
 *
 * <p>Scopes are provided via the {@link Provider} interface and found by looking for
 * the current {@link com.google.common.flogger.context.ScopedLoggingContext ScopedLoggingContexts}.
 *
 * <p>Stateful fluent logging APIs which need to look up per log site information (e.g. rate limit
 * state) should do so via a {@link LogSiteMap} using the {@link LogSiteKey} passed into the {@link
 * LogContext#postProcess(LogSiteKey)} method. If scopes are present in the log site {@link
 * Metadata} then the log site key provided to the {@code postProcess()} method will already be
 * specialized to take account of any scopes present.
 *
 * <p>Note that scopes have no effect when applied to stateless log statements (e.g. log statements
 * without rate limiting) since the log site key for that log statement will not be used in any
 * maps.
 */
public abstract class LoggingScope {
  /**
   * Creates a scope which automatically removes any associated keys from {@link LogSiteMap}s when
   * it's garbage collected. The given label is used only for debugging purposes and may appear in
   * log statements, it should not contain any user data or other runtime information.
   */
  // TODO: Strongly consider making the label a compile time constant.
  public static LoggingScope create(String label) {
    return new WeakScope(checkNotNull(label, "label"));
  }

  private final String label;

  /**
   * Creates a basic scope with the specified label. Custom subclasses of {@code LoggingScope} must
   * manage their own lifecycles to avoid leaking memory and polluting {@link LogSiteMap}s with
   * unused keys.
   */
  protected LoggingScope(String label) {
    this.label = label;
  }

  /**
   * Returns a specialization of the given key which accounts for this scope instance. Two
   * specialized keys should compare as {@link Object#equals(Object)} if and only if they are
   * specializations from the same log site, with the same sequence of scopes applied.
   *
   * <p>The returned instance:
   *
   * <ul>
   *   <li>Must be an immutable "value type".
   *   <li>Must not compare as {@link Object#equals(Object)} to the given key.
   *   <li>Should have a different {@link Object#hashCode()} to the given key.
   *   <li>Should be efficient and lightweight.
   * </ul>
   *
   * As such it is recommended that the {@link SpecializedLogSiteKey#of(LogSiteKey, Object)} method
   * is used in implementations, passing in a suitable qualifier (which need not be the scope
   * itself, but must be unique per scope).
   */
  protected abstract LogSiteKey specialize(LogSiteKey key);

  /**
   * Registers "hooks" which should be called when this scope is "closed". The hooks are intended to
   * remove the keys associated with this scope from any data structures they may be held in, to
   * avoid leaking allocations.
   *
   * <p>Note that a key may be specialized with several scopes and the first scope to be closed will
   * remove it from any associated data structures (conceptually the scope that a log site is called
   * from is the intersection of all the currently active scopes which apply to it).
   */
  protected abstract void onClose(Runnable removalHook);

  @Override
  public final String toString() {
    return label;
  }

  // VisibleForTesting
  static final class WeakScope extends LoggingScope {
    // Do NOT reference the Scope directly from a specialized key, use the "key part"
    // to avoid the key from keeping the Scope instance alive. When the scope becomes
    // unreachable, the key part weak reference is enqueued which triggers tidyup at
    // the next call to specializeForScopesIn() where scopes are used.
    //
    // This must be unique per scope since it acts as a qualifier within specialized
    // log site keys. Using a different weak reference per specialized key would not
    // work (which is part of the reason we also need the "on close" queue as well as
    // the reference queue).
    private final KeyPart keyPart;

    public WeakScope(String label) {
      super(label);
      this.keyPart = new KeyPart(this);
    }

    @Override
    protected LogSiteKey specialize(LogSiteKey key) {
      return SpecializedLogSiteKey.of(key, keyPart);
    }

    @Override
    protected void onClose(Runnable remove) {
      // Clear the reference queue about as often as we would add a new key to a map.
      // This  should still mean that the queue is almost always empty when we check
      // it (since we expect more than one specialized log site key per scope) and it
      // avoids spamming the queue clearance loop for every log statement and avoids
      // class loading the reference queue until we know scopes have been used.
      KeyPart.removeUnusedKeys();
      keyPart.onCloseHooks.offer(remove);
    }

    void closeForTesting() {
      keyPart.close();
    }

    // Class is only loaded once we've seen scopes in action (Android doesn't like
    // eager class loading and many Android apps won't use scopes).
    // This forms part of each log site key, some must have singleton semantics.
    private static class KeyPart extends WeakReference<LoggingScope> {
      private static final ReferenceQueue<LoggingScope> queue = new ReferenceQueue<LoggingScope>();

      private final Queue<Runnable> onCloseHooks = new ConcurrentLinkedQueue<Runnable>();

      KeyPart(LoggingScope scope) {
        super(scope, queue);
      }

      // If this were ever too "bursty" due to removal of many keys for the same scope,
      // we could modify this code to process only a maximum number of removals each
      // time and keep a single "in progress" KeyPart around until next time.
      static void removeUnusedKeys() {
        // There are always more specialized keys than entries in the reference queue,
        // so the queue should be empty most of the time we get here.
        for (KeyPart p = (KeyPart) queue.poll(); p != null; p = (KeyPart) queue.poll()) {
          p.close();
        }
      }

      private void close() {
        // This executes once for each map entry created in the enclosing scope. It is
        // very dependent on logging usage in the scope and theoretically unbounded.
        for (Runnable r = onCloseHooks.poll(); r != null; r = onCloseHooks.poll()) {
          r.run();
        }
      }
    }
  }
}
