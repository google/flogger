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

import com.google.common.flogger.LoggingScope;
import com.google.common.flogger.context.LogLevelMap;
import com.google.common.flogger.context.ScopeMetadata;
import com.google.common.flogger.context.ScopeType;
import com.google.common.flogger.context.ScopedLoggingContext.ScopeList;
import com.google.common.flogger.context.Tags;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A mutable thread-safe holder for context scoped logging information. */
final class Log4j2ContextData {

  static Tags getTagsFor(@NullableDecl Log4j2ContextData context) {
    if (context != null) {
      Tags tags = context.tagRef.get();
      if (tags != null) {
        return tags;
      }
    }
    return Tags.empty();
  }

  static ScopeMetadata getMetadataFor(@NullableDecl Log4j2ContextData context) {
    if (context != null) {
      ScopeMetadata metadata = context.metadataRef.get();
      if (metadata != null) {
        return metadata;
      }
    }
    return ScopeMetadata.none();
  }

  static boolean shouldForceLoggingFor(
          @NullableDecl Log4j2ContextData context, String loggerName, Level level) {
    if (context != null) {
      LogLevelMap map = context.logLevelMapRef.get();
      if (map != null) {
        return map.getLevel(loggerName).intValue() <= level.intValue();
      }
    }
    return false;
  }

  @NullableDecl
  static LoggingScope lookupScopeFor(@NullableDecl Log4j2ContextData contextData, ScopeType type) {
    return contextData != null ? ScopeList.lookup(contextData.scopes, type) : null;
  }

  private abstract static class ScopedReference<T> {
    private final AtomicReference<T> value;

    ScopedReference(@NullableDecl T initialValue) {
      this.value = new AtomicReference<T>(initialValue);
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
  private final ScopedReference<ScopeMetadata> metadataRef;
  private final ScopedReference<LogLevelMap> logLevelMapRef;

  Log4j2ContextData(@NullableDecl Log4j2ContextData parent, @NullableDecl ScopeType scopeType) {
    this.scopes = ScopeList.addScope(parent != null ? parent.scopes : null, scopeType);
    this.tagRef =
        new ScopedReference<Tags>(parent != null ? parent.tagRef.get() : null) {
          @Override
          Tags merge(Tags current, Tags delta) {
            return current.merge(delta);
          }
        };
    this.metadataRef =
        new ScopedReference<ScopeMetadata>(parent != null ? parent.metadataRef.get() : null) {
          @Override
          ScopeMetadata merge(ScopeMetadata current, ScopeMetadata delta) {
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
  }

  void addTags(@NullableDecl Tags tags) {
    tagRef.mergeFrom(tags);
  }

  void addMetadata(@NullableDecl ScopeMetadata metadata) {
    metadataRef.mergeFrom(metadata);
  }

  void applyLogLevelMap(@NullableDecl LogLevelMap logLevelMap) {
    if (logLevelMap != null) {
      // Set the global flag to trigger testing of the log level map from now on (we only apply a
      // log level map to an active context or one that's about to become active).
      Log4j2ContextDataProvider.INSTANCE.setLogLevelMapFlag();
      logLevelMapRef.mergeFrom(logLevelMap);
    }
  }
}
