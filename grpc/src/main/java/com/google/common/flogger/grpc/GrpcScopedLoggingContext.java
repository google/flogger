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
import com.google.common.flogger.context.ContextMetadata;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopeType;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.Tags;
import io.grpc.Context;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A gRPC context based implementation of Flogger's scoped logging context API. This is a lazily
 * loaded singleton instance returned from {@link GrpcContextDataProvider#getContextApiSingleton()}
 * which provides application code with a mechanism for controlling logging contexts.
 *
 * <p>It is vital that this class is lazily loaded (rather than being loaded when the logging
 * platform is configured) since other classes it uses may well use fluent loggers.
 */
final class GrpcScopedLoggingContext extends ScopedLoggingContext {

  private final GrpcContextDataProvider provider;

  GrpcScopedLoggingContext(GrpcContextDataProvider provider) {
    this.provider = provider;
  }

  @Override
  public ScopedLoggingContext.Builder newContext() {
    return newBuilder(null);
  }

  @Override
  public ScopedLoggingContext.Builder newContext(ScopeType scopeType) {
    return newBuilder(scopeType);
  }

  private ScopedLoggingContext.Builder newBuilder(@NullableDecl ScopeType scopeType) {
    return new ScopedLoggingContext.Builder() {
      @Override
      public LoggingContextCloseable install() {
        GrpcContextData newContextData =
            new GrpcContextData(GrpcContextDataProvider.currentContext(), scopeType, provider);
        newContextData.addTags(getTags());
        newContextData.addMetadata(getMetadata());
        newContextData.applyLogLevelMap(getLogLevelMap());
        return installContextData(newContextData);
      }
    };
  }

  private static LoggingContextCloseable installContextData(GrpcContextData newContextData) {
    // Capture these variables outside the lambda.
    Context newGrpcContext =
        Context.current().withValue(GrpcContextDataProvider.getContextKey(), newContextData);
    @SuppressWarnings("MustBeClosedChecker")
    Context prev = newGrpcContext.attach();
    return () -> newGrpcContext.detach(prev);
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
    ContextMetadata metadata = ContextMetadata.singleton(key, value);
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
