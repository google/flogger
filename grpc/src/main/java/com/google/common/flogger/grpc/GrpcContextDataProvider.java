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

import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.ScopeMetadata;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.Tags;
import io.grpc.Context;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A gRPC context based implementation of Flogger's ContextDataProvider. */
public final class GrpcContextDataProvider extends ContextDataProvider {
  // Package visible since the config instance needs to poke the "hasLogLevelMap" flags. This field
  // should be the only thing that needs to be initialized when this class is loaded, since class
  // loading can occur during logging platform initialization.
  static final GrpcContextDataProvider INSTANCE = new GrpcContextDataProvider();

  /** Invoked during logging platform initialization (so not allowed to do any logging). */
  public static ContextDataProvider getInstance() {
    return INSTANCE;
  }

  // Strictly a singleton (even though it has mutable state).
  private GrpcContextDataProvider() {}

  // When this is false we can skip some work for every log statement. This is set to true if _any_
  // context adds a log level map at any point (this is generally rare and only used for targeted
  // debugging so will often never occur during normal application use). This is never reset.
  private volatile boolean hasLogLevelMap = false;

  // For use by GrpcScopedLoggingContext (same package). We cannot define the keys in there because
  // this class must not depend on GrpcScopedLoggingContext during static initialization. We must
  // also delay initializing this value (via a lazy-holder) to avoid any risks during logger
  // initialization.
  static Context.Key<GrpcContextData> getContextKey() {
    return KeyHolder.GRPC_SCOPE;
  }

  /** Returns the current context data, or {@code null} if we are not in a context. */
  @NullableDecl
  static GrpcContextData currentContext() {
    return getContextKey().get();
  }

  /** Sets the flag to enable checking for a log level map after one is set for the first time. */
  void setLogLevelMapFlag() {
    hasLogLevelMap = true;
  }

  @Override
  public ScopedLoggingContext getContextApiSingleton() {
    // This is a lazy-holder to avoid requiring the API instance to be initiated at the same time
    // as the LoggingContext (which is created as the Platform instance is initialized). By doing
    // this we break any static initialization cycles and allow the config API perform its own
    // logging if necessary. DO NOT cache the config instance in a static field in this class!!
    return GrpcScopedLoggingContext.getGrpcConfigInstance();
  }

  @Override
  public Tags getTags() {
    return GrpcContextData.getTagsFor(currentContext());
  }

  @Override
  public ScopeMetadata getMetadata() {
    return GrpcContextData.getMetadataFor(currentContext());
  }

  @Override
  public boolean shouldForceLogging(String loggerName, Level level, boolean isEnabledByLevel) {
    // Shortcutting boolean saves doing any work in the commonest case (this code is called for
    // every log statement, which is 100-1000 times more than just the  enabled log statements).
    return hasLogLevelMap
        && GrpcContextData.shouldForceLoggingFor(currentContext(), loggerName, level);
  }

  // Static lazy-holder to avoid needing to call unknown code during Flogger initialization. While
  // gRPC context keys don't trigger any logging now, it's not certain that this is guaranteed.
  private static final class KeyHolder {
    private static final Context.Key<GrpcContextData> GRPC_SCOPE =
        Context.key("Flogger gRPC scope");

    private KeyHolder() {}
  }
}
