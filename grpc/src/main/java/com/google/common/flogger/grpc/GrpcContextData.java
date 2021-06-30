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

import com.google.common.flogger.LoggingScope;
import com.google.common.flogger.context.ContextMetadata;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopeType;
import com.google.common.flogger.context.ScopedLoggingContext.ScopeList;
import com.google.common.flogger.context.Tags;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A mutable thread-safe holder for context scoped logging information. */
final class GrpcContextData {

  static Tags getTagsFor(@NullableDecl GrpcContextData context) {
    if (context != null) {
      Tags tags = context.tagRef.get();
      if (tags != null) {
        return tags;
      }
    }
    return Tags.empty();
  }

  static ContextMetadata getMetadataFor(@NullableDecl GrpcContextData context) {
    if (context != null) {
      ContextMetadata metadata = context.metadataRef.get();
      if (metadata != null) {
        return metadata;
      }
    }
    return ContextMetadata.none();
  }

  static boolean shouldForceLoggingFor(
      @NullableDecl GrpcContextData context, String loggerName, Level level) {
    if (context != null) {
      LogLevelMap map = context.logLevelMapRef.get();
      if (map != null) {
        return map.getLevel(loggerName).intValue() <= level.intValue();
      }
    }
    return false;
  }

  @NullableDecl
  static LoggingScope lookupScopeFor(@NullableDecl GrpcContextData contextData, ScopeType type) {
    return contextData != null ? ScopeList.lookup(contextData.scopes, type) : null;
  }

  private abstract static class ScopedReference<T> {
    private final AtomicReference<T> value;

    ScopedReference(@NullableDecl T initialValue) {
      this.value = new AtomicReference<>(initialValue);
    }

    @NullableDecl
    final T get() {
      return value.get();
    }

    // Note: If we could use Java 1.8 runtime libraries, this would just be "accumulateAndGet()",
    // but gRPC is Java 1.7 compatible: https://github.com/grpc/grpc-java/blob/master/README.md
    final void mergeFrom(@NullableDecl T delta) {
      if (delta != null) {
        T current;
        do {
          current = get();
        } while (!value.compareAndSet(current, current != null ? merge(current, delta) : delta));
      }
    }

    abstract T merge(T current, T delta);
  }

  @NullableDecl private final ScopeList scopes;
  private final ScopedReference<Tags> tagRef;
  private final ScopedReference<ContextMetadata> metadataRef;
  private final ScopedReference<LogLevelMap> logLevelMapRef;
  // Only needed to register that log level maps are being used (as a performance optimization).
  private final GrpcContextDataProvider provider;

  GrpcContextData(
      @NullableDecl GrpcContextData parent,
      @NullableDecl ScopeType scopeType,
      GrpcContextDataProvider provider) {
    this.scopes = ScopeList.addScope(parent != null ? parent.scopes : null, scopeType);
    this.tagRef =
        new ScopedReference<Tags>(parent != null ? parent.tagRef.get() : null) {
          @Override
          Tags merge(Tags current, Tags delta) {
            return current.merge(delta);
          }
        };
    this.metadataRef =
        new ScopedReference<ContextMetadata>(parent != null ? parent.metadataRef.get() : null) {
          @Override
          ContextMetadata merge(ContextMetadata current, ContextMetadata delta) {
            return current.concatenate(delta);
          }
        };
    this.logLevelMapRef =
        new ScopedReference<LogLevelMap>(parent != null ? parent.logLevelMapRef.get() : null) {
          @Override
          LogLevelMap merge(LogLevelMap current, LogLevelMap delta) {
            return current.merge(delta);
          }
        };
    this.provider = provider;
  }

  void addTags(@NullableDecl Tags tags) {
    tagRef.mergeFrom(tags);
  }

  void addMetadata(@NullableDecl ContextMetadata metadata) {
    metadataRef.mergeFrom(metadata);
  }

  void applyLogLevelMap(@NullableDecl LogLevelMap logLevelMap) {
    if (logLevelMap != null) {
      // Set the global flag to trigger testing of the log level map from now on (we only apply a
      // log level map to an active context or one that's about to become active).
      provider.setLogLevelMapFlag();
      logLevelMapRef.mergeFrom(logLevelMap);
    }
  }
}
