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

package com.google.common.flogger.grpc;

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopeMetadata;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.Tags;
import com.google.errorprone.annotations.CheckReturnValue;
import io.grpc.Context;

/**
 * A gRPC context based implementation of Flogger's scoped logging context API. This is a lazily
 * loaded singleton instance returned from {@link GrpcContextDataProvider#getContextApiSingleton()}
 * which provides application code with a mechanism for controlling logging contexts.
 *
 * <p>It is vital that this class is lazily loaded (rather than being loaded when the logging
 * platform is configured) since other classes it uses may well use fluent loggers.
 */
final class GrpcScopedLoggingContext extends ScopedLoggingContext {
  private static final ScopedLoggingContext INSTANCE = new GrpcScopedLoggingContext();

  static ScopedLoggingContext getGrpcConfigInstance() {
    return INSTANCE;
  }

  @Override
  @CheckReturnValue
  public ScopedLoggingContext.Builder newScope() {
    return new ScopedLoggingContext.Builder() {
      @Override
      public LoggingContextCloseable install() {
        GrpcContextData newContextData =
            GrpcContextData.create(GrpcContextDataProvider.currentContext());
        newContextData.addTags(getTags());
        newContextData.addMetadata(getMetadata());
        newContextData.applyLogLevelMap(getLogLevelMap());
        return installContextData(newContextData);
      }
    };
  }

  private static LoggingContextCloseable installContextData(GrpcContextData newContextData) {
    // Capture these variables outside the lambda.
    Context context =
        Context.current().withValue(GrpcContextDataProvider.getContextKey(), newContextData);
    @SuppressWarnings("MustBeClosedChecker")
    Context prev = context.attach();
    return () -> context.detach(prev);
  }

  @Override
  public boolean addTags(Tags tags) {
    checkNotNull(tags, "tags");
    GrpcContextData context = GrpcContextDataProvider.currentContext();
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
    GrpcContextData context = GrpcContextDataProvider.currentContext();
    if (context != null) {
      context.addMetadata(metadata);
      return true;
    }
    return false;
  }

  @Override
  public boolean applyLogLevelMap(LogLevelMap logLevelMap) {
    checkNotNull(logLevelMap, "log level map");
    GrpcContextData context = GrpcContextDataProvider.currentContext();
    if (context != null) {
      context.applyLogLevelMap(logLevelMap);
      return true;
    }
    return false;
  }
}
