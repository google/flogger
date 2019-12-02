/*
 * Copyright (C) 2019 The Flogger Authors.
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

package com.google.common.flogger.context;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * The no-op data provider used when no other ContextDataProvider is explicitly installed. This
 * class is loaded during logger platform initialization.
 */
final class NoOpContextDataProvider extends ContextDataProvider {
  private static final class LazyHolder {
    private static final ScopedLoggingContext NO_OP_CONTEXT = new NoOpContext();
  }

  @Override
  public ScopedLoggingContext getContextApiSingleton() {
    // Delay class loading of NoOpScopedLoggingContext until it's actually used, which should
    // mean it's safe to them log from that class without circular static initialization issues.
    return LazyHolder.NO_OP_CONTEXT;
  }

  @Override
  public boolean shouldForceLogging(String loggerName, Level level, boolean isEnabledByLevel) {
    return false;
  }

  @Override
  public Tags getTags() {
    return Tags.empty();
  }

  /**
   * The no-op logging context used when no ContextDataProvider is explicitly installed. This class
   * is lazily loaded only when first used.
   */
  private static final class NoOpContext extends ScopedLoggingContext {
    private final AtomicBoolean haveWarned = new AtomicBoolean();

    private static final Closeable NO_OP_CLOSEABLE =
        new Closeable() {
          @Override
          public void close() {}
        };

    NoOpContext() {}

    @Override
    public Closeable withNewScope() {
      if (haveWarned.compareAndSet(false, true)) {
        System.err.format(
            "%s#withNewScope() was called, but no implementation of %s was installed.\n"
                + "\tLogging scopes, and the use of tags or log forcing will have no effect.\n"
                + "\tSet the system property '%s' to install a %s.",
            ScopedLoggingContext.class.getName(),
            ContextDataProvider.class.getName(),
            CONTEXT_PROVIDER_PROPERTY,
            ContextDataProvider.class.getSimpleName());
      }
      return NO_OP_CLOSEABLE;
    }

    @Override
    public boolean applyLogLevelMap(LogLevelMap m) {
      return false;
    }

    @Override
    public boolean addTags(Tags tags) {
      return false;
    }
  }
}
