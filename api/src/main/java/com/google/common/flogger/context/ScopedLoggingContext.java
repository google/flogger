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
import java.util.concurrent.Callable;

/**
 * A user facing API for creating and modifying scoped logging contexts in applications.
 *
 * <p>Scoped contexts provide a way for application code to attach metadata and control the
 * behaviour of logging within well defined scopes. This is most often associated with making "per
 * request" modifications to logging behaviour such as:
 *
 * <ul>
 *   <li>Adding a request ID to every log statement.
 *   <li>Forcing logging at a finer level for a specific request (e.g. based on a URL debug
 *       parameter).
 * </ul>
 *
 * <p>In order to modify a scope, one must first be "installed" by the application. This is
 * typically done by core application or framework code at the start of a request (e.g. via a Guice
 * or Dagger module). Once installed, the scope can be modified by any code called from within it,
 * and once a scope terminates, it reverts to any previously installed scope.
 *
 * <p>Note that since logging contexts are designed to be modified by code in libraries and helper
 * functions which do not know about each other, the data structures and behaviour of logging
 * contexts are carefully designed to avoid any accidental "undoing" of existing behaviour. In
 * particular:
 *
 * <ul>
 *   <li>Tags can only be added to scopes, never modified or removed.
 *   <li>Logging that's enabled by one scope cannot be disabled from within a nested scope.
 * </ul>
 *
 * <p>One possibly surprising result of this behaviour is that it's not possible to disable logging
 * from within a scope. However this is quite intentional, since overly verbose logging should be
 * fixed by other mechanisms (code changes, global logging configuration), and not on a "per
 * request" basis.
 *
 * <p>Depending on the framework used, it's possible that the current logging scope will be
 * automatically propagated to some or all threads or sub-tasks started from within the scope. This
 * is not guaranteed however and the semantic behaviour of scope propagation is not defined by this
 * class.
 *
 * <p>Context support and automatic propagation is heavily reliant on Java platform capabilities,
 * and precise behaviour is likely to differ between runtime environments or frameworks. Context
 * propagation may not behave the same everywhere, and in some situations logging contexts may not
 * be supported at all. Methods which attempt to affect context state may do nothing in some
 * environments, or when called at some points in an application. If application code relies on
 * modifications to logging contexts, it should always check the return values of any modification
 * methods called (e.g. {@link #addTags(Tags)}).
 */
public abstract class ScopedLoggingContext {
  /**
   * Returns the platform/framework specific implementation of the logging context API. This is a
   * singleton value and need not be cached by callers. If logging contexts are not supported, this
   * method will return an empty context implementation which returns {@code false} from any
   * modification methods.
   */
  public static ScopedLoggingContext getInstance() {
    return ContextDataProvider.getInstance().getContextApiSingleton();
  }

  protected ScopedLoggingContext() {}

  /**
   * Installs a new context scope to which additional logging metadata can be attached. The caller
   * is <em>required</em> to invoke {@link Closeable#close() close()} on the returned instances in
   * the reverse order to which they were obtained. For JDK 1.7 and above, this is best achieved by
   * using a try-with-resources construction in the calling code.
   *
   * <pre>{@code
   * ScopedLoggingContext ctx = ScopedLoggingContext.getInstance();
   * try (Closeable scope = ctx.withNewScope()) {
   *   // Now add metadata to the installed context (returns false if not supported).
   *   ctx.addTag("my_log_tag", someValue);
   *
   *   // Any logging by code called from within this scope will contain the additional metadata.
   *   logger.atInfo().log("Log message should contain tag value...");
   * }
   * }</pre>
   *
   * <p>To avoid the need to manage scopes manually, it is strongly recommended that the helper
   * methods, such as {@link #wrap(Runnable)} or {@link #run(Runnable)} are used to simplify the
   * handling of scopes. This method is intended primarily to be overridden by context
   * implementations rather than being invoked as a normal part of context use.
   *
   * <p>An implementation of scoped contexts must preserve any existing metadata when a scope is
   * opened, and restore the previous state when it terminates. In particular:
   *
   * <pre>{@code
   * ScopedLoggingContext ctx = ScopedLoggingContext.getInstance();
   * logger.atInfo().log("Some log statement with existing tags and behaviour...");
   * try (Closeable scope = ctx.withNewScope()) {
   *   logger.atInfo().log("This log statement is the same as the first...");
   *   ctx.addTag("new_tag", "some value");
   *   logger.atInfo().log("This log statement has the new tag present...");
   * }
   * logger.atInfo().log("This log statement is the same as the first...");
   * }</pre>
   *
   * <p>Note that the returned {@link Closeable} is not required to enforce the correct closure of
   * nested scopes, and while it is permitted to throw a {@link InvalidLoggingScopeStateException}
   * in the face of mismatched or invalid usage, it is not required.
   */
  public abstract Closeable withNewScope();

  /**
   * Applies the given log level map to the current scope. Log level settings are merged with any
   * existing setting from the current (or parent) scopes such that logging will be enabled for a
   * log statement if:
   *
   * <ul>
   *   <li>it was enabled by the given map.
   *   <li>it was already enabled by the current context.
   * </ul>
   *
   * <p>The effects of this call will be undone only when the current scope terminates.
   *
   * @return false if there is no current scope, or scoped contexts are not supported.
   */
  public abstract boolean applyLogLevelMap(LogLevelMap m);

  /**
   * Adds a tags to the current set of log tags for the current scope. Tags are merged together and
   * existing tags cannot be modified. This is deliberate since two pieces of code may not know
   * about each other and could accidentally use the same tag name; in that situation it's important
   * that both tag values are preserved.
   *
   * <p>Furthermore, the types of data allowed for tag values are strictly controlled. This is also
   * very deliberate since these tags must be efficiently added to every log statement and so it's
   * important that they resulting string representation is reliably cacheable and can be calculated
   * without invoking arbitrary code (e.g. the {@code toString()} method of some unknown user type).
   *
   * @return false if there is no current scope, or scoped contexts are not supported.
   */
  public abstract boolean addTags(Tags tags);

  /**
   * Wraps a runnable so it will execute within a new context scope.
   *
   * @throws InvalidLoggingScopeStateException if the scope created during this method cannot be
   *     closed correctly (e.g. if a nested scope has also been opened, but not closed).
   */
  public final Runnable wrap(final Runnable r) {
    return new Runnable() {
      @Override
      public void run() {
        // JDK 1.6 does not have "try-with-resources"
        Closeable scope = withNewScope();
        boolean hasError = true;
        try {
          r.run();
          hasError = false;
        } finally {
          closeAndMaybePropagateError(scope, hasError);
        }
      }
    };
  }

  /**
   * Wraps a callable so it will execute within a new context scope.
   *
   * @throws InvalidLoggingScopeStateException if the scope created during this method cannot be
   *     closed correctly (e.g. if a nested scope has also been opened, but not closed).
   */
  public final <R> Callable<R> wrap(final Callable<R> c) {
    return new Callable<R>() {
      @Override
      public R call() throws Exception {
        Closeable scope = withNewScope();
        boolean hasError = true;
        try {
          R result = c.call();
          hasError = false;
          return result;
        } finally {
          closeAndMaybePropagateError(scope, hasError);
        }
      }
    };
  }

  private static void closeAndMaybePropagateError(Closeable scope, boolean callerHasError) {
    try {
      scope.close();
    } catch (Throwable e) {
      // Don't supersede the "original" user exception (if there was one).
      if (!callerHasError) {
        throw (e instanceof InvalidLoggingScopeStateException)
            ? ((InvalidLoggingScopeStateException) e)
            : new InvalidLoggingScopeStateException("invalid logging context state", e);
      }
    }
  }

  /** Runs a runnable directly within a new context scope. */
  public final void run(Runnable r) {
    wrap(r).run();
  }

  /** Calls a callable directly within a new context scope. */
  public final <R> R call(Callable<R> c) throws Exception {
    return wrap(c).call();
  }

  /**
   * Thrown if it can be determined that context scopes have been closed incorrectly. Note that the
   * point at which this exception is thrown may not itself be the point where the mishandling
   * occurred, but simply where it was first detected.
   */
  public static final class InvalidLoggingScopeStateException extends IllegalStateException {
    public InvalidLoggingScopeStateException(String message, Throwable cause) {
      super(message, cause);
    }

    public InvalidLoggingScopeStateException(String message) {
      super(message);
    }
  }
}
