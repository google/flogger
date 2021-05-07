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

package com.google.common.flogger.backend.system;

import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopeType;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.ScopedLoggingContext.LoggingContextCloseable;
import com.google.common.flogger.context.Tags;
/**
 * @deprecated Replaced by ContextDataProvider.
 */
// TODO(b/173778154): Delete this class once nothing external relies on it.
@Deprecated
public abstract class LoggingContext extends ContextDataProvider {
  // Needed temporarily while old LoggingContext based implementations are migrated away from.
  private static final ScopedLoggingContext NO_OP_API = new NoOpScopedLoggingContext();

  @Override
  public ScopedLoggingContext getContextApiSingleton() {
    return NO_OP_API;
  }

  private static final class NoOpScopedLoggingContext extends ScopedLoggingContext
      implements LoggingContextCloseable {
    @Override
    public ScopedLoggingContext.Builder newContext() {
      return new ScopedLoggingContext.Builder() {
        @Override
        public LoggingContextCloseable install() {
          return NoOpScopedLoggingContext.this;
        }
      };
    }

    @Override
    public Builder newContext(ScopeType scopeType) {
      // Scopes unsupported in the old LoggingContext based implementations.
      return newContext();
    }

    @Override
    public void close() {}

    @Override
    public boolean addTags(Tags tags) {
      return false;
    }

    @Override
    public boolean applyLogLevelMap(LogLevelMap m) {
      return false;
    }
  }
}
