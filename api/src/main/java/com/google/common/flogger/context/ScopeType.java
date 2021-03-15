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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.LoggingScope;
import com.google.common.flogger.LoggingScopeProvider;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Singleton keys which identify different types of scopes which scoped contexts can be bound to.
 *
 * <p>To bind a context to a scope type, create the context with that type:
 *
 * <pre>{@code
 * ScopedLoggingContext.getInstance().newScope(REQUEST).run(() -> someTask(...));
 * }</pre>
 */
public final class ScopeType implements LoggingScopeProvider {
  /**
   * The built in "request" scope. This can be bound to a scoped context in order to provide a
   * distinct request scope for each context, allowing stateful logging operations (e.g. rate
   * limiting) to be scoped to the current request.
   *
   * <p>Enable a request scope using:
   *
   * <pre>{@code
   * ScopedLoggingContext.getInstance().newScope(REQUEST).run(() -> scopedMethod(x, y, z));
   * }</pre>
   *
   * which runs {@code scopedMethod} with a new "request" scope for the duration of the context.
   *
   * <p>Then use per-request rate limiting using:
   *
   * <pre>{@code
   * logger.atWarning().atMostEvery(5, SECONDS).per(REQUEST).log("Some error message...");
   * }</pre>
   *
   * Note that in order for the request scope to be applied to a log statement, the {@code
   * per(REQUEST)} method must still be called; just being inside the request scope isn't enough.
   */
  public static final ScopeType REQUEST = create("request");

  /**
   * Creates a new Scope type, which can be used as a singleton key to identify a scope during
   * scoped context creation or logging. Callers are expected to retain this key in a static field
   * or return it via a static method. Scope types have singleton semantics and two scope types with
   * the same name are <em>NOT</em> equivalent.
   *
   * @param name a debug friendly scope identifier (e.g. "my_batch_job").
   */
  public static ScopeType create(String name) {
    return new ScopeType(name);
  }

  private final String name;

  private ScopeType(String name) {
    this.name = checkNotNull(name, "name");
  }


  // Called by ScopedLoggingContext to make a new scope instance when a context is installed.
  LoggingScope newScope() {
    return LoggingScope.create(name);
  }

  @NullableDecl
  @Override
  public LoggingScope getCurrentScope() {
    return ContextDataProvider.getInstance().getScope(this);
  }
}
