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

import com.google.common.flogger.LoggingScope;
import com.google.common.flogger.MetadataKey;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.Closeable;
import java.util.concurrent.Callable;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A user facing API for creating and modifying scoped logging contexts in applications.
 *
 * <p>Scoped contexts provide a way for application code to attach metadata and control the
 * behaviour of logging within well defined contexts. This is most often associated with making "per
 * request" modifications to logging behaviour such as:
 *
 * <ul>
 *   <li>Adding a request ID to every log statement.
 *   <li>Forcing logging at a finer level for a specific request (e.g. based on a URL debug
 *       parameter).
 * </ul>
 *
 * <p>Contexts are nestable and new contexts can be added to provide additional metadata which will
 * be available to logging as long as the context is installed.
 *
 * <p>Note that in the current API contexts are also modifiable after creation, but this usage is
 * discouraged and may be removed in future. The problem with modifying contexts after creation is
 * that, since contexts can be shared between threads, it is potentially confusing if tags are added
 * to a context when it is being used concurrently by multiple threads.
 *
 * <p>Note that since logging contexts are designed to be modified by code in libraries and helper
 * functions which do not know about each other, the data structures and behaviour of logging
 * contexts are carefully designed to avoid any accidental "undoing" of existing behaviour. In
 * particular:
 *
 * <ul>
 *   <li>Tags can only be added to contexts, never modified or removed.
 *   <li>Logging that's enabled by one context cannot be disabled from within a nested context.
 * </ul>
 *
 * <p>One possibly surprising result of this behaviour is that it's not possible to disable logging
 * from within a context. However this is quite intentional, since overly verbose logging should be
 * fixed by other mechanisms (code changes, global logging configuration), and not on a "per
 * request" basis.
 *
 * <p>Depending on the framework used, it's possible that the current logging context will be
 * automatically propagated to some or all threads or sub-tasks started from within the context.
 * This is not guaranteed however and the semantic behaviour of context propagation is not defined
 * by this class.
 *
 * <p>In particular, if you haven't explicitly opened a context in which to run your code, there is
 * no guarantee that a default "global" context exists. In this case any attempts to add metadata
 * (e.g. via {@link #addTags}) will fail, returning {@code false}.
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
  /** A logging context which must be closed in the reverse order to which it was created. */
  // If Flogger is bumped to JDK 1.7, this should be switched to AutoCloseable.
  public interface LoggingContextCloseable extends Closeable {
    // Overridden to remove the throws clause allowing simple try-with-resources use.
    @Override
    public void close();
  }

  /** Lightweight internal helper class for context implementations to manage a list of scopes. */
  public static final class ScopeList {
    /**
     * Adds a new scope to the list for the given type. If the given type is null, or a scope for
     * the type already exists in the list, the original (potentially {@code null}) list reference
     * is returned.
     */
    @NullableDecl public static ScopeList addScope(
        @NullableDecl ScopeList list, @NullableDecl ScopeType type) {
      return (type != null && lookup(list, type) == null)
          ? new ScopeList(type, type.newScope(), list)
          : list;
    }

    /** Finds a scope instance for the given type in a possibly null scope list. */
    @NullableDecl public static LoggingScope lookup(@NullableDecl ScopeList list, ScopeType type) {
      while (list != null) {
        if (type.equals(list.key)) {
          return list.scope;
        }
        list = list.next;
      }
      return null;
    }

    private final ScopeType key;
    private final LoggingScope scope;
    @NullableDecl private final ScopeList next;

    public ScopeList(ScopeType key, LoggingScope scope, @NullableDecl ScopeList next) {
      this.key = checkNotNull(key, "scope type");
      this.scope = checkNotNull(scope, "scope");
      this.next = next;
    }
  }

  /**
   * A fluent builder API for creating and installing new context scopes. This API should be used
   * whenever the metadata to be added to a scope is known at the time the scope is created.
   *
   * <p>This class is intended to be used only as part of a fluent statement, and retaining a
   * reference to a builder instance for any length of time is not recommended.
   */
  public abstract static class Builder {
    private Tags tags = null;
    private ContextMetadata.Builder metadata = null;
    private LogLevelMap logLevelMap = null;

    protected Builder() {}

    /**
     * Sets the tags to be used with the context. This method can be called at most once per
     * builder.
     */
    @CanIgnoreReturnValue
    public final Builder withTags(Tags tags) {
      checkState(this.tags == null, "tags already set");
      checkNotNull(tags, "tags");
      this.tags = tags;
      return this;
    }

    /**
     * Adds a single metadata key/value pair to the context. This method can be called multiple
     * times on a builder.
     */
    @CanIgnoreReturnValue
    public final <T> Builder withMetadata(MetadataKey<T> key, T value) {
      if (metadata == null) {
        metadata = ContextMetadata.builder();
      }
      metadata.add(key, value);
      return this;
    }

    /**
     * Sets the log level map to be used with the context being built. This method can be called at
     * most once per builder.
     */
    @CanIgnoreReturnValue
    public final Builder withLogLevelMap(LogLevelMap logLevelMap) {
      checkState(this.logLevelMap == null, "log level map already set");
      checkNotNull(logLevelMap, "log level map");
      this.logLevelMap = logLevelMap;
      return this;
    }

    /**
     * Wraps a runnable so it will execute within a new context based on the state of the builder.
     * Note that each time this runnable is executed, a new context will be installed extending from
     * the currently installed context at the time of execution.
     *
     * @throws InvalidLoggingScopeStateException if the context created during this method cannot be
     *     closed correctly (e.g. if a nested context has also been opened, but not closed).
     */
    public final Runnable wrap(final Runnable r) {
      return new Runnable() {
        @Override
        @SuppressWarnings("MustBeClosedChecker")
        public void run() {
          // JDK 1.6 does not have "try-with-resources"
          LoggingContextCloseable context = install();
          boolean hasError = true;
          try {
            r.run();
            hasError = false;
          } finally {
            closeAndMaybePropagateError(context, hasError);
          }
        }
      };
    }

    /**
     * Wraps a callable so it will execute within a new context based on the state of the builder.
     * Note that each time this runnable is executed, a new context will be installed extending from
     * the currently installed context at the time of execution.
     *
     * @throws InvalidLoggingScopeStateException if the context created during this method cannot be
     *     closed correctly (e.g. if a nested context has also been opened, but not closed).
     */
    public final <R> Callable<R> wrap(final Callable<R> c) {
      return new Callable<R>() {
        @Override
        @SuppressWarnings("MustBeClosedChecker")
        public R call() throws Exception {
          LoggingContextCloseable context = install();
          boolean hasError = true;
          try {
            R result = c.call();
            hasError = false;
            return result;
          } finally {
            closeAndMaybePropagateError(context, hasError);
          }
        }
      };
    }

    /** Runs a runnable directly within a new context installed from this builder. */
    public final void run(Runnable r) {
      wrap(r).run();
    }

    /** Calls a {@link Callable} directly within a new context installed from this builder. */
    @CanIgnoreReturnValue
    public final <R> R call(Callable<R> c) throws Exception {
      return wrap(c).call();
    }

    /**
     * Calls a {@link Callable} directly within a new context installed from this builder, wrapping
     * any checked exceptions with a {@link RuntimeException}.
     */
    @CanIgnoreReturnValue
    public final <R> R callUnchecked(Callable<R> c) {
      try {
        return call(c);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException("checked exception caught during context call", e);
      }
    }

    /**
     * Installs a new context based on the state of the builder. The caller is <em>required</em> to
     * invoke {@link LoggingContextCloseable#close() close()} on the returned instances in the
     * reverse order to which they were obtained. For JDK 1.7 and above, this is best achieved by
     * using a try-with-resources construction in the calling code.
     *
     * <pre>{@code
     * try (LoggingContextCloseable ctx = ScopedLoggingContext.getInstance()
     *     .newContext().withTags(Tags.of("my_tag", someValue).install()) {
     *   // Logging by code called from within this context will contain the additional metadata.
     *   logger.atInfo().log("Log message should contain tag value...");
     * }
     * }</pre>
     *
     * <p>To avoid the need to manage contexts manually, it is strongly recommended that the helper
     * methods, such as {@link #wrap(Runnable)} or {@link #run(Runnable)} are used to simplify the
     * handling of contexts. This method is intended primarily to be overridden by context
     * implementations rather than being invoked as a normal part of context use.
     *
     * <p>An implementation of scoped contexts must preserve any existing metadata when a context is
     * opened, and restore the previous state when it terminates.
     *
     * <p>Note that the returned {@link LoggingContextCloseable} is not required to enforce the
     * correct closure of nested contexts, and while it is permitted to throw a {@link
     * InvalidLoggingScopeStateException} in the face of mismatched or invalid usage, it is not
     * required.
     */
    @MustBeClosed
    public abstract LoggingContextCloseable install();

    /**
     * Returns the configured tags, or null. This method may do work and results should be cached by
     * context implementations.
     */
    @NullableDecl
    protected final Tags getTags() {
      return tags;
    }

    /**
     * Returns the configured context metadata, or null. This method may do work and results should
     * be cached by context implementations.
     */
    @NullableDecl
    protected final ContextMetadata getMetadata() {
      return metadata != null ? metadata.build() : null;
    }

    /**
     * Returns the configured log level map, or null. This method may do work and results should be
     * cached by context implementations.
     */
    @NullableDecl
    protected final LogLevelMap getLogLevelMap() {
      return logLevelMap;
    }
  }

  /**
   * Returns the platform/framework specific implementation of the logging context API. This is a
   * singleton value and need not be cached by callers. If logging contexts are not supported, this
   * method will return an empty context implementation which has no effect.
   */
  public static ScopedLoggingContext getInstance() {
    return ContextDataProvider.getInstance().getContextApiSingleton();
  }

  protected ScopedLoggingContext() {}

  /**
   * Creates a new context builder to which additional logging metadata can be attached before being
   * installed or used to wrap some existing code.
   *
   * <pre>{@code
   * ScopedLoggingContext ctx = ScopedLoggingContext.getInstance();
   * Foo result = ctx.newContext().withTags(Tags.of("my_tag", someValue)).call(MyClass::doFoo);
   * }</pre>
   *
   * <p>Implementations of this API must return a subclass of {@link Builder} which can install all
   * necessary metadata into a new context from the builder's current state.
   *
   * <p>Note for users: if you don't need an instance of {@code ScopedLoggingContext} for some
   * reason such as testability (injecting it, for example), consider using the static methods in
   * {@link ScopedLoggingContexts} instead to avoid the need to call {@link #getInstance}:
   *
   * <pre>{@code
   * Foo result = ScopedLoggingContexts.newContext()
   *     .withTags(Tags.of("my_tag", someValue))
   *     .call(MyClass::doFoo);
   * }</pre>
   */
  public abstract Builder newContext();

  /**
   * Creates a new context builder to which additional logging metadata can be attached before being
   * installed or used to wrap some existing code.
   *
   * <p>This method is the same as {@link #newContext()} except it additionally binds a new {@link
   * ScopeType} instance to the newly created context. This allows log statements to control
   * stateful logging operations (e.g. rate limiting) using the {@link
   * com.google.common.flogger.LoggingApi#per(ScopeType) per(ScopeType)} method.
   *
   * <p>Note for users: if you don't need an instance of {@code ScopedLoggingContext} for some
   * reason such as testability (injecting it, for example), consider using the static methods in
   * {@link ScopedLoggingContexts} instead to avoid the need to call {@link #getInstance}.
   */
  public abstract Builder newContext(ScopeType scopeType);

  /**
   * Deprecated equivalent to {@link #newContext()}.
   *
   * @deprecated implementers and callers should use {@link #newContext()} instead. This method will
   *     be removed in the next Flogger release.
   */
  @Deprecated
  public
  Builder newScope() {
    return newContext();
  }

  /**
   * Adds tags to the current set of log tags for the current context. Tags are merged together and
   * existing tags cannot be modified. This is deliberate since two pieces of code may not know
   * about each other and could accidentally use the same tag name; in that situation it's important
   * that both tag values are preserved.
   *
   * <p>Furthermore, the types of data allowed for tag values are strictly controlled. This is also
   * very deliberate since these tags must be efficiently added to every log statement and so it's
   * important that they resulting string representation is reliably cacheable and can be calculated
   * without invoking arbitrary code (e.g. the {@code toString()} method of some unknown user type).
   *
   * @return false if there is no current context, or scoped contexts are not supported.
   */
  @CanIgnoreReturnValue
  public boolean addTags(Tags tags) {
    checkNotNull(tags, "tags");
    return false;
  }

  /**
   * Adds a single metadata key/value pair to the current context.
   *
   * <p>Unlike {@link Tags}, which have a well defined value ordering, independent of the order in
   * which values were added, context metadata preserves the order of addition. As such, it is not
   * advised to add values for the same metadata key from multiple threads, since that may create
   * non-deterministic ordering. It is recommended (where possible) to add metadata when building a
   * new context, rather than adding it to context visible to multiple threads.
   */
  @CanIgnoreReturnValue
  public <T> boolean addMetadata(MetadataKey<T> key, T value) {
    checkNotNull(key, "key");
    checkNotNull(value, "value");
    return false;
  }

  /**
   * Applies the given log level map to the current context. Log level settings are merged with any
   * existing setting from the current (or parent) contexts such that logging will be enabled for a
   * log statement if:
   *
   * <ul>
   *   <li>It was enabled by the given map.
   *   <li>It was already enabled by the current context.
   * </ul>
   *
   * <p>The effects of this call will be undone only when the current context terminates.
   *
   * @return false if there is no current context, or scoped contexts are not supported.
   */
  @CanIgnoreReturnValue
  public boolean applyLogLevelMap(LogLevelMap logLevelMap) {
    checkNotNull(logLevelMap, "log level map");
    return false;
  }

  private static void closeAndMaybePropagateError(
      LoggingContextCloseable context, boolean callerHasError) {
    // Because LoggingContextCloseable is not just a "Closeable" there's no risk of it throwing any
    // checked
    // exceptions. Inparticular, when this is switched to use AutoCloseable, there's no risk of
    // having to deal with InterruptedException. That's why having an extended interface is always
    // better than using [Auto]Closeable directly.
    try {
      context.close();
    } catch (RuntimeException e) {
      // This method is always called from a finally block which may be about to rethrow a user
      // exception, so ignore any errors during close() if that's the case.
      if (!callerHasError) {
        throw (e instanceof InvalidLoggingContextStateException)
            ? ((InvalidLoggingContextStateException) e)
            : new InvalidLoggingContextStateException("invalid logging context state", e);
      }
    }
  }

  /**
   * Thrown if it can be determined that contexts have been closed incorrectly. Note that the point
   * at which this exception is thrown may not itself be the point where the mishandling occurred,
   * but simply where it was first detected.
   */
  public static final class InvalidLoggingContextStateException extends IllegalStateException {
    public InvalidLoggingContextStateException(String message, Throwable cause) {
      super(message, cause);
    }

    public InvalidLoggingContextStateException(String message) {
      super(message);
    }
  }

  /** Package private checker to help avoid unhelpful debug logs. */
  boolean isNoOp() {
    return false;
  }
}
