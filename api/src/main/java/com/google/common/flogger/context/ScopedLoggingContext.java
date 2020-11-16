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
import static com.google.common.flogger.util.Checks.checkState;

import com.google.common.flogger.MetadataKey;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.MustBeClosed;
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
 * <p>Scopes are nestable and new scopes can be added to provide additional metadata which will be
 * available to logging as long as the scope is installed.
 *
 * <p>Note that in the current API scopes are also modifiable after creation, but this usage is
 * discouraged and may be removed in future. The problem with modifying scopes after creation is
 * that, since scopes can be shared between threads, it is potentially confusing if tags are added
 * to a scope when it is being used concurrently by multiple threads.
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
 * modifications to an existing, implicit logging context, it should always check the return values
 * of any modification methods called (e.g. {@link #addTags(Tags)}).
 */
public abstract class ScopedLoggingContext {
  /** A logging scope which must be closed in the reverse order to which it was created. */
  // If Flogger is bumped to JDK 1.7, this should be switched to AutoCloseable.
  public interface LoggingScope extends Closeable {
    // Overridden to remove the throws clause allowing simple try-with-resources use.
    @Override
    public void close();
  }

  /**
   * A fluent builder API for creating and installing new context scopes. This API should be used
   * whenever the metadata to be added to a scope it known at the time the scope is created.
   *
   * <p>This class is intended to be used only as part of a fluent statement, and retaining a
   * reference to a builder instance for any length of time is not recommended.
   */
  public abstract class Builder {
    private Tags tags = null;
    private ScopeMetadata.Builder metadata = null;
    private LogLevelMap logLevelMap = null;

    protected Builder() {}

    /**
     * Sets the tags to be used with the scope. This method can be called at most once per builder.
     */
    @CheckReturnValue
    public final Builder withTags(Tags tags) {
      checkState(this.tags == null, "tags already set");
      checkNotNull(tags, "tags");
      this.tags = tags;
      return this;
    }

    /**
     * Adds a single metadata key/value pair to the scope. This method can be called multiple times
     * on a builder.
     */
    @CheckReturnValue
    public final <T> Builder withMetadata(MetadataKey<T> key, T value) {
      if (metadata == null) {
        metadata = ScopeMetadata.builder();
      }
      metadata.add(key, value);
      return this;
    }

    /**
     * Sets the log level map to be used with the scope being built. This method can be called at
     * most once per builder.
     */
    @CheckReturnValue
    public final Builder withLogLevelMap(LogLevelMap logLevelMap) {
      checkState(this.logLevelMap == null, "log level map already set");
      checkNotNull(logLevelMap, "log level map");
      this.logLevelMap = logLevelMap;
      return this;
    }

    /**
     * Wraps a runnable so it will execute within a new context scope based on the state of the
     * builder. Note that each time this runnable is executed, a new scope will be installed
     * extending from the currently installed scope at the time of execution.
     *
     * @throws InvalidLoggingScopeStateException if the scope created during this method cannot be
     *     closed correctly (e.g. if a nested scope has also been opened, but not closed).
     */
    @CheckReturnValue
    public final Runnable wrap(final Runnable r) {
      return new Runnable() {
        @Override
        @SuppressWarnings("MustBeClosedChecker")
        public void run() {
          // JDK 1.6 does not have "try-with-resources"
          LoggingScope scope = install();
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
     * Wraps a callable so it will execute within a new context scope based on the state of the
     * builder. Note that each time this runnable is executed, a new scope will be installed
     * extending from the currently installed scope at the time of execution.
     *
     * @throws InvalidLoggingScopeStateException if the scope created during this method cannot be
     *     closed correctly (e.g. if a nested scope has also been opened, but not closed).
     */
    @CheckReturnValue
    public final <R> Callable<R> wrap(final Callable<R> c) {
      return new Callable<R>() {
        @Override
        @SuppressWarnings("MustBeClosedChecker")
        public R call() throws Exception {
          LoggingScope scope = install();
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

    /** Runs a runnable directly within a new context scope installed from this builder. */
    public final void run(Runnable r) {
      wrap(r).run();
    }

    /** Calls a callable directly within a new context scope installed from this builder. */
    public final <R> R call(Callable<R> c) throws Exception {
      return wrap(c).call();
    }

    /**
     * Installs a new context scope based on the state of the builder. The caller is
     * <em>required</em> to invoke {@link LoggingScope#close() close()} on the returned instances in
     * the reverse order to which they were obtained. For JDK 1.7 and above, this is best achieved
     * by using a try-with-resources construction in the calling code.
     *
     * <pre>{@code
     * ScopedLoggingContext ctx = ScopedLoggingContext.getInstance();
     * try (LoggingScope scope = ctx.newScope().withTags(Tags.of("my_tag", someValue).install()) {
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
     * opened, and restore the previous state when it terminates.
     *
     * <p>Note that the returned {@link LoggingScope} is not required to enforce the correct closure
     * of nested scopes, and while it is permitted to throw a {@link
     * InvalidLoggingScopeStateException} in the face of mismatched or invalid usage, it is not
     * required.
     */
    @MustBeClosed
    @CheckReturnValue
    public abstract LoggingScope install();

    /**
     * Returns the configured tags, or null. This method may do work and results should be cached by
     * context implementations.
     */
    protected final Tags getTags() {
      return tags;
    }

    /**
     * Returns the configured scope metadata, or null. This method may do work and results should be
     * cached by context implementations.
     */
    protected final ScopeMetadata getMetadata() {
      return metadata != null ? metadata.build() : null;
    }

    /**
     * Returns the configured log level map, or null. This method may do work and results should be
     * cached by context implementations.
     */
    protected final LogLevelMap getLogLevelMap() {
      return logLevelMap;
    }
  }

  /**
   * Returns the platform/framework specific implementation of the logging context API. This is a
   * singleton value and need not be cached by callers. If logging contexts are not supported, this
   * method will return an empty context implementation which has no effect.
   */
  @CheckReturnValue
  public static ScopedLoggingContext getInstance() {
    return ContextDataProvider.getInstance().getContextApiSingleton();
  }

  protected ScopedLoggingContext() {}

  /**
   * Creates a new context scope builder to which additional logging metadata can be attached before
   * being installed or used to wrap some existing code.
   *
   * <pre>{@code
   * ScopedLoggingContext ctx = ScopedLoggingContext.getInstance();
   * Foo result = ctx.newScope().withTags(Tags.of("my_tag", someValue)).call(MyClass::doFoo);
   * }</pre>
   *
   * <p>Note that the default implementation of this method is potentially inefficient and it is
   * strongly recommended that scoped context implementations override it with a better
   * implementation.
   */
  // TODO(dbeaumont): Verify this is no longer needed and make it abstract instead.
  @CheckReturnValue
  public Builder newScope() {
    // This implementation only exists while the scoped context implementations do not all support
    // this method directly. It should be removed once implementations are updated to support
    // creating contexts directly with state, rather than creating and then modifying them.
    return new Builder() {
      @Override
      @SuppressWarnings("MustBeClosedChecker")
      public LoggingScope install() {
        LoggingScope scope = withNewScope();
        try {
          Tags tags = getTags();
          if (tags != null && !tags.isEmpty()) {
            addTags(tags);
          }
          // Adding metadata one at a time is very inefficient. Subclass implementations can do
          // better by simply setting the metadata directly.
          ScopeMetadata metadata = getMetadata();
          if (metadata != null) {
            for (int n = 0, size = metadata.size(); n < size; n++) {
              add(metadata.getKey(n), metadata.getValue(n));
            }
          }
          LogLevelMap logLevelMap = getLogLevelMap();
          if (logLevelMap != null) {
            applyLogLevelMap(logLevelMap);
          }
          return scope;
        } catch (Error e) {
          forceClose(scope);
          throw e;
        } catch (RuntimeException e) {
          forceClose(scope);
          throw e;
        }
      }

      // Recapture the key type so we can cast the value.
      <T> void add(MetadataKey<T> key, Object value) {
        addMetadata(key, key.cast(value));
      }
    };
  }

  private static void forceClose(LoggingScope scope) {
    // Errors in scope setup should almost never happen since no user code is being run here,
    // but we should still protect the scope state as best we can (e.g. OutOfMemoryError).
    // Make a best effort attempt to close the scope before re-throwing the error. Logging
    // context implementations should never fail when closing a successfully opened context.
    try {
      scope.close();
    } catch (Throwable ignored) {
      // Ignored since we have something better to throw, and this shouldn't happen anyway.
    }
  }

  /**
   * Installs a new context scope to which additional logging metadata can be attached. The caller
   * is <em>required</em> to invoke {@link LoggingScope#close() close()} on the returned instances
   * in the reverse order to which they were obtained. For JDK 1.7 and above, this is best achieved
   * by using a try-with-resources construction in the calling code.
   *
   * <pre>{@code
   * ScopedLoggingContext ctx = ScopedLoggingContext.getInstance();
   * try (LoggingScope scope = ctx.withNewScope()) {
   *   // Now add metadata to the installed context (returns false if not supported).
   *   ctx.addTags(Tags.of("my_tag", someValue));
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
   * try (LoggingScope scope = ctx.withNewScope()) {
   *   logger.atInfo().log("This log statement is the same as the first...");
   *   ctx.addTags(Tags.of("my_tag", someValue));
   *   logger.atInfo().log("This log statement has the new tag present...");
   * }
   * logger.atInfo().log("This log statement is the same as the first...");
   * }</pre>
   *
   * <p>Note that the returned {@link LoggingScope} is not required to enforce the correct closure
   * of nested scopes, and while it is permitted to throw a {@link
   * InvalidLoggingScopeStateException} in the face of mismatched or invalid usage, it is not
   * required.
   *
   * @deprecated Prefer using {@link #newScope()} and the builder API to configure scopes before
   *     they are installed.
   */
  @Deprecated
  @MustBeClosed
  @CheckReturnValue
  public abstract LoggingScope withNewScope();

  /**
   * Wraps a runnable so it will execute within a new context scope.
   *
   * @throws InvalidLoggingScopeStateException if the scope created during this method cannot be
   *     closed correctly (e.g. if a nested scope has also been opened, but not closed).
   * @deprecated Prefer using {@link #newScope()} and the builder API to configure scopes before
   *     they are installed.
   */
  @Deprecated
  @CheckReturnValue
  public final Runnable wrap(final Runnable r) {
    return newScope().wrap(r);
  }

  /**
   * Wraps a callable so it will execute within a new context scope.
   *
   * @throws InvalidLoggingScopeStateException if the scope created during this method cannot be
   *     closed correctly (e.g. if a nested scope has also been opened, but not closed).
   * @deprecated Prefer using {@link #newScope()} and the builder API to configure scopes before
   *     they are installed.
   */
  @Deprecated
  @CheckReturnValue
  public final <R> Callable<R> wrap(final Callable<R> c) {
    return newScope().wrap(c);
  }

  /**
   * Runs a runnable directly within a new context scope.
   *
   * @deprecated Prefer using {@link #newScope()} and the builder API to configure scopes before
   *     they are installed.
   */
  @Deprecated
  public final void run(Runnable r) {
    newScope().run(r);
  }

  /**
   * Calls a callable directly within a new context scope.
   *
   * @deprecated Prefer using {@link #newScope()} and the builder API to configure scopes before
   *     they are installed.
   */
  @Deprecated
  public final <R> R call(Callable<R> c) throws Exception {
    return newScope().call(c);
  }

  /**
   * Adds tags to the current set of log tags for the current scope. Tags are merged together and
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
  public boolean addTags(Tags tags) {
    checkNotNull(tags, "tags");
    return false;
  }

  /**
   * Adds a single metadata key/value pair to the current scope.
   *
   * <p>Unlike {@link Tags}, which have a well defined value ordering, independent of the order in
   * which values were added, scope metadata preserves the order of addition. As such, it is not
   * advised to add values for the same metadata key from multiple threads, since that may create
   * non-deterministic ordering. It is recommended (where possible) to add metadata when building a
   * new scope, rather than adding it to context visible to multiple threads.
   */
  public <T> boolean addMetadata(MetadataKey<T> key, T value) {
    checkNotNull(key, "key");
    checkNotNull(value, "value");
    return false;
  }

  /**
   * Applies the given log level map to the current scope. Log level settings are merged with any
   * existing setting from the current (or parent) scopes such that logging will be enabled for a
   * log statement if:
   *
   * <ul>
   *   <li>It was enabled by the given map.
   *   <li>It was already enabled by the current context.
   * </ul>
   *
   * <p>The effects of this call will be undone only when the current scope terminates.
   *
   * @return false if there is no current scope, or scoped contexts are not supported.
   */
  public boolean applyLogLevelMap(LogLevelMap logLevelMap) {
    checkNotNull(logLevelMap, "log level map");
    return false;
  }

  private static void closeAndMaybePropagateError(LoggingScope scope, boolean callerHasError) {
    // Because LoggingScope is not just a "Closeable" there's no risk of it throwing any checked
    // exceptions. Inparticular, when this is switched to use AutoCloseable, there's no risk of
    // having to deal with InterruptedException. That's why having an extended interface is always
    // better than using [Auto]Closeable directly.
    try {
      scope.close();
    } catch (RuntimeException e) {
      // This method is always called from a finally block which may be about to rethrow a user
      // exception, so ignore any errors during close() if that's the case.
      if (!callerHasError) {
        throw (e instanceof InvalidLoggingScopeStateException)
            ? ((InvalidLoggingScopeStateException) e)
            : new InvalidLoggingScopeStateException("invalid logging context state", e);
      }
    }
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
