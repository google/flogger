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

package com.google.common.flogger.backend.log4j2;

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopeMetadata;
import com.google.common.flogger.context.ScopeType;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.Tags;
import com.google.errorprone.annotations.CheckReturnValue;
import io.grpc.Context;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A gRPC context based implementation of Flogger's scoped logging context API. This is a lazily
 * loaded singleton instance returned from {@link Log4j2ContextDataProvider#getContextApiSingleton()}
 * which provides application code with a mechanism for controlling logging contexts.
 *
 * <p>It is vital that this class is lazily loaded (rather than being loaded when the logging
 * platform is configured) since other classes it uses may well use fluent loggers.
 */
final class Log4j2ScopedLoggingContext extends ScopedLoggingContext {
  private static final ScopedLoggingContext INSTANCE = new Log4j2ScopedLoggingContext();

  static ScopedLoggingContext getGrpcConfigInstance() {
    return INSTANCE;
  }

  @Override
  @CheckReturnValue
  public Builder newContext() {
    return newBuilder(null);
  }

  private Builder newBuilder(@NullableDecl final ScopeType scopeType) {
    return new Builder() {
      @Override
      public LoggingContextCloseable install() {
        Log4j2ContextData newContextData =
            new Log4j2ContextData(Log4j2ContextDataProvider.currentContext(), scopeType);
        newContextData.addTags(getTags());
        newContextData.addMetadata(getMetadata());
        newContextData.applyLogLevelMap(getLogLevelMap());
        return installContextData(newContextData);
      }
    };
  }

  private static LoggingContextCloseable installContextData(Log4j2ContextData newContextData) {
    // Capture these variables outside the lambda.
    final Context newGrpcContext =
            Context.current().withValue(Log4j2ContextDataProvider.getContextKey(), newContextData);
    @SuppressWarnings("MustBeClosedChecker")
    final Context prev = newGrpcContext.attach();
    return new LoggingContextCloseable() {
      @Override
      public void close() {
        newGrpcContext.detach(prev);
      }
    };
  }

  @Override
  public boolean addTags(Tags tags) {
    checkNotNull(tags, "tags");
    Log4j2ContextData context = Log4j2ContextDataProvider.currentContext();
    if (context != null) {
      context.addTags(tags);
      return true;
    }
    return false;
  }

  @Override
  public <T> boolean addMetadata(MetadataKey<T> key, T value) {
    // Serves as the null pointer check, and we don't care much about the extra allocation in the
    // case where there's no context, because that should be very rare (and the singleton is small).
    ScopeMetadata metadata = ScopeMetadata.singleton(key, value);
    Log4j2ContextData context = Log4j2ContextDataProvider.currentContext();
    if (context != null) {
      context.addMetadata(metadata);
      return true;
    }
    return false;
  }

  @Override
  public boolean applyLogLevelMap(LogLevelMap logLevelMap) {
    checkNotNull(logLevelMap, "log level map");
    Log4j2ContextData context = Log4j2ContextDataProvider.currentContext();
    if (context != null) {
      context.applyLogLevelMap(logLevelMap);
      return true;
    }
    return false;
  }
}
