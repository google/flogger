/*
 * Copyright (C) 2012 The Flogger Authors.
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

package com.google.common.flogger;

import static com.google.common.flogger.util.CallerFinder.getStackForCallerOf;
import static com.google.common.flogger.util.Checks.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.flogger.DurationRateLimiter.RateLimitPeriod;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.TemplateContext;
import com.google.common.flogger.context.Tags;
import com.google.common.flogger.parser.MessageParser;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * The base context for a logging statement, which implements the base logging API.
 *
 * <p>This class is an implementation of the base {@link LoggingApi} interface and acts as a holder
 * for any state applied to the log statement during the fluent call sequence. The lifecycle of a
 * logging context is very short; it is created by a logger, usually in response to a call to the
 * {@link AbstractLogger#at(Level)} method, and normally lasts only as long as the log statement.
 *
 * <p>This class should not be visible to normal users of the logging API and it is only needed when
 * extending the API to add more functionality. In order to extend the logging API and add methods
 * to the fluent call chain, the {@code LoggingApi} interface should be extended to add any new
 * methods, and this class should be extended to implement them. A new logger class will then be
 * needed to return the extended context.
 *
 * <p>Logging contexts are not thread safe.
 */
public abstract class LogContext<LOGGER extends AbstractLogger<API>, API extends LoggingApi<API>>
    implements LoggingApi<API>, LogData {

  /**
   * The predefined metadata keys used by the default logging API. Backend implementations can use
   * these to identify metadata added by the core logging API.
   */
  // TODO: Reevaluate this whole strategy before open-sourcing.
  public static final class Key {
    private Key() {}
    /**
     * The key associated with a {@link Throwable} cause to be associated with the log message. This
     * value is set by {@link LoggingApi#withCause(Throwable)}.
     */
    public static final MetadataKey<Throwable> LOG_CAUSE =
        MetadataKey.single("cause", Throwable.class);

    /**
     * The key associated with a rate limiting counter for "1-in-N" rate limiting. The value is set
     * by {@link LoggingApi#every(int)}.
     */
    public static final MetadataKey<Integer> LOG_EVERY_N =
        MetadataKey.single("ratelimit_count", Integer.class);

    /**
     * The key associated with a rate limiting counter for "1-in-N" randomly sampled rate limiting.
     * The value is set by {@link LoggingApi#onAverageEvery(int)}.
     */
    public static final MetadataKey<Integer> LOG_SAMPLE_EVERY_N =
        MetadataKey.single("sampling_count", Integer.class);

    /**
     * The key associated with a rate limiting period for "at most once every N" rate limiting. The
     * value is set by {@link LoggingApi#atMostEvery(int, TimeUnit)}.
     */
    public static final MetadataKey<RateLimitPeriod> LOG_AT_MOST_EVERY =
        MetadataKey.single("ratelimit_period", RateLimitPeriod.class);

    /**
     * The key associated with a count of rate limited logs. This is only public so backends can
     * reference the key to control formatting.
     */
    public static final MetadataKey<Integer> SKIPPED_LOG_COUNT =
        MetadataKey.single("skipped", Integer.class);

    /**
     * The key associated with a sequence of log site "grouping keys". These serve to specialize the
     * log site key to group the behaviour of stateful operations like rate limiting. This is used
     * by the {@code per()} methods and is only public so backends can reference the key to control
     * formatting.
     */
    public static final MetadataKey<Object> LOG_SITE_GROUPING_KEY =
        new MetadataKey<Object>("group_by", Object.class, true) {
          @Override
          public void emitRepeated(Iterator<Object> keys, KeyValueHandler out) {
            if (keys.hasNext()) {
              Object first = keys.next();
              if (!keys.hasNext()) {
                out.handle(getLabel(), first);
              } else {
                // In the very unlikely case there's more than one aggregation key, emit a list.
                StringBuilder buf = new StringBuilder();
                buf.append('[').append(first);
                do {
                  buf.append(',').append(keys.next());
                } while (keys.hasNext());
                out.handle(getLabel(), buf.append(']').toString());
              }
            }
          }
        };

    /**
     * The key associated with a {@code Boolean} value used to specify that the log statement must
     * be emitted.
     *
     * <p>Forcing a log statement ensures that the {@code LoggerBackend} is passed the {@code
     * LogData} for this log statement regardless of the backend's log level or any other filtering
     * or rate limiting which might normally occur. If a log statement is forced, this key will be
     * set immediately on creation of the logging context and will be visible to both fluent methods
     * and post-processing.
     *
     * <p>Filtering and rate-limiting methods must check for this value and should treat forced log
     * statements as not having had any filtering or rate limiting applied. For example, if the
     * following log statement was forced:
     *
     * <pre>{@code
     * logger.atInfo().withCause(e).atMostEvery(1, MINUTES).log("Message...");
     * }</pre>
     *
     * it should behave as if the rate-limiting methods were never called, such as:
     *
     * <pre>{@code
     * logger.atInfo().withCause(e).log("Message...");
     * }</pre>
     *
     * As well as no longer including any rate-limiting metadata for the forced log statement, this
     * also has the effect of never interfering with the rate-limiting of this log statement for
     * other callers.
     *
     * <p>The decision of whether to force a log statement is expected to be made based upon debug
     * values provded by the logger which come from a scope greater than the log statement itself.
     * Thus it makes no sense to provide a public method to set this value programmatically for a
     * log statement.
     */
    public static final MetadataKey<Boolean> WAS_FORCED =
        MetadataKey.single("forced", Boolean.class);

    /**
     * The key associated with any injected {@link Tags}.
     *
     * <p>If tags are injected, they are added after post-processing if the log site is enabled.
     * Thus they are not available to the {@code postProcess()} method itself. The rationale is that
     * a log statement's behavior should only be affected by code at the log site (other than
     * "forcing" log statements, which is slightly a special case).
     *
     * <p>Tags can be added at the log site, although this should rarely be necessary and using
     * normal log message arguments is always the preferred way to indicate unstrctured log data.
     * Users should never build new {@link Tags} instances just to pass them into a log statement.
     */
    public static final MetadataKey<Tags> TAGS =
        new MetadataKey<Tags>("tags", Tags.class, false) {
          @Override
          public void emit(Tags tags, KeyValueHandler out) {
            for (Map.Entry<String, ? extends Set<Object>> e : tags.asMap().entrySet()) {
              Set<Object> values = e.getValue();
              if (!values.isEmpty()) {
                for (Object v : e.getValue()) {
                  out.handle(e.getKey(), v);
                }
              } else {
                out.handle(e.getKey(), null);
              }
            }
          }
        };

    /**
     * Key associated with the metadata for specifying additional stack information with a log
     * statement.
     */
    public static final MetadataKey<StackSize> CONTEXT_STACK_SIZE =
        MetadataKey.single("stack_size", StackSize.class);
  }

  static final class MutableMetadata extends Metadata {
    /**
     * The default number of key/value pairs we initially allocate space for when someone adds
     * metadata to this context.
     *
     * <p>Note: As of 10/12 the VM allocates small object arrays very linearly with respect to the
     * number of elements (an array has a 12 byte header with 4 bytes/element for object
     * references). The allocation size is always rounded up to the next 8 bytes which means we can
     * just pick a small value for the initial size and grow from there without too much waste.
     *
     * <p>For 4 key/value pairs, we will need 8 elements in the array, which will take up 48 bytes
     * {@code (12 + (8 * 4) = 44}, which when rounded up is 48.
     */
    private static final int INITIAL_KEY_VALUE_CAPACITY = 4;

    /**
     * The array of key/value pairs to hold any metadata the might be added by the logger or any of
     * the fluent methods on our API. This is an array so it is as space efficient as possible.
     */
    private Object[] keyValuePairs = new Object[2 * INITIAL_KEY_VALUE_CAPACITY];
    /** The number of key/value pairs currently stored in the array. */
    private int keyValueCount = 0;

    @Override
    public int size() {
      return keyValueCount;
    }

    @Override
    public MetadataKey<?> getKey(int n) {
      if (n >= keyValueCount) {
        throw new IndexOutOfBoundsException();
      }
      return (MetadataKey<?>) keyValuePairs[2 * n];
    }

    @Override
    public Object getValue(int n) {
      if (n >= keyValueCount) {
        throw new IndexOutOfBoundsException();
      }
      return keyValuePairs[(2 * n) + 1];
    }

    private int indexOf(MetadataKey<?> key) {
      for (int index = 0; index < keyValueCount; index++) {
        if (keyValuePairs[2 * index].equals(key)) {
          return index;
        }
      }
      return -1;
    }

    @Override
    @NullableDecl
    public <T> T findValue(MetadataKey<T> key) {
      int index = indexOf(key);
      return index != -1 ? key.cast(keyValuePairs[(2 * index) + 1]) : null;
    }

    /**
     * Adds the key/value pair to the metadata (growing the internal array as necessary). If the key
     * cannot be repeated, and there is already a value for the key in the metadata, then the
     * existing value is replaced, otherwise the value is added at the end of the metadata.
     */
    <T> void addValue(MetadataKey<T> key, T value) {
      if (!key.canRepeat()) {
        int index = indexOf(key);
        if (index != -1) {
          keyValuePairs[(2 * index) + 1] = checkNotNull(value, "metadata value");
          return;
        }
      }
      // Check that the array is big enough for one more element.
      if (2 * (keyValueCount + 1) > keyValuePairs.length) {
        // Use doubling here (this code should almost never be hit in normal usage and the total
        // number of items should always stay relatively small. If this resizing algorithm is ever
        // modified it is vital that the new value is always an even number.
        keyValuePairs = Arrays.copyOf(keyValuePairs, 2 * keyValuePairs.length);
      }
      keyValuePairs[2 * keyValueCount] = checkNotNull(key, "metadata key");
      keyValuePairs[(2 * keyValueCount) + 1] = checkNotNull(value, "metadata value");
      keyValueCount += 1;
    }

    /** Removes all key/value pairs for a given key. */
    void removeAllValues(MetadataKey<?> key) {
      int index = indexOf(key);
      if (index >= 0) {
        int dest = 2 * index;
        int src = dest + 2;
        while (src < (2 * keyValueCount)) {
          Object nextKey = keyValuePairs[src];
          if (!nextKey.equals(key)) {
            keyValuePairs[dest] = nextKey;
            keyValuePairs[dest + 1] = keyValuePairs[src + 1];
            dest += 2;
          }
          src += 2;
        }
        // We know src & dest are +ve and (src > dest), so shifting is safe here.
        keyValueCount -= (src - dest) >> 1;
        while (dest < src) {
          keyValuePairs[dest++] = null;
        }
      }
    }

    /** Strictly for debugging. */
    @Override
    public String toString() {
      StringBuilder out = new StringBuilder("Metadata{");
      for (int n = 0; n < size(); n++) {
        out.append(" '").append(getKey(n)).append("': ").append(getValue(n));
      }
      return out.append(" }").toString();
    }
  }

  /**
   * A simple token used to identify cases where a single literal value is logged. Note that this
   * instance must be unique and it is important not to replace this with {@code ""} or any other
   * value than might be interned and be accessible to code outside this class.
   */
  private static final String LITERAL_VALUE_MESSAGE = new String();

  // TODO: Aggressively attempt to reduce the number of fields in this instance.

  /** The log level of the log statement that this context was created for. */
  private final Level level;
  /** The timestamp of the log statement that this context is associated with. */
  private final long timestampNanos;

  /** Additional metadata for this log statement (added via fluent API methods). */
  private MutableMetadata metadata = null;
  /** The log site information for this log statement (set immediately prior to post-processing). */
  private LogSite logSite = null;
  /** Rate limit status (only set if rate limiting occurs). */
  private RateLimitStatus rateLimitStatus = null;
  /** The template context if formatting is required (set only after post-processing). */
  private TemplateContext templateContext = null;
  /** The log arguments (set only after post-processing). */
  private Object[] args = null;

  /**
   * Creates a logging context with the specified level, and with a timestamp obtained from the
   * configured logging {@link Platform}.
   *
   * @param level the log level for this log statement.
   * @param isForced whether to force this log statement (see {@link #wasForced()} for details).
   */
  protected LogContext(Level level, boolean isForced) {
    this(level, isForced, Platform.getCurrentTimeNanos());
  }

  /**
   * Creates a logging context with the specified level and timestamp. This constructor is provided
   * only for testing when timestamps need to be injected. In general, subclasses would only need to
   * call this constructor when testing additional API methods which require timestamps (e.g.
   * additional rate limiting functionality). Most unit tests for logger subclasses should not test
   * the value of the timestamp at all, since this is already well tested elsewhere.
   *
   * @param level the log level for this log statement.
   * @param isForced whether to force this log statement (see {@link #wasForced()} for details).
   * @param timestampNanos the nanosecond timestamp for this log statement.
   */
  protected LogContext(Level level, boolean isForced, long timestampNanos) {
    this.level = checkNotNull(level, "level");
    this.timestampNanos = timestampNanos;
    if (isForced) {
      addMetadata(Key.WAS_FORCED, Boolean.TRUE);
    }
  }

  /**
   * Returns the current API (which is just the concrete sub-type of this instance). This is
   * returned by fluent methods to continue the fluent call chain.
   */
  protected abstract API api();

  // ---- Logging Context Constants ----

  /**
   * Returns the logger which created this context. This is implemented as an abstract method to
   * save a field in every context.
   */
  protected abstract LOGGER getLogger();

  /**
   * Returns the constant no-op logging API, which can be returned by fluent methods in extended
   * logging contexts to efficiently disable logging. This is implemented as an abstract method to
   * save a field in every context.
   */
  protected abstract API noOp();

  /** Returns the message parser used for all log statements made through this logger. */
  protected abstract MessageParser getMessageParser();

  // ---- LogData API ----

  @Override
  public final Level getLevel() {
    return level;
  }

  @Deprecated
  @Override
  public final long getTimestampMicros() {
    return NANOSECONDS.toMicros(timestampNanos);
  }

  @Override
  public final long getTimestampNanos() {
    return timestampNanos;
  }

  @Override
  public final String getLoggerName() {
    return getLogger().getBackend().getLoggerName();
  }

  @Override
  public final LogSite getLogSite() {
    if (logSite == null) {
      throw new IllegalStateException("cannot request log site information prior to postProcess()");
    }
    return logSite;
  }

  @Override
  public final TemplateContext getTemplateContext() {
    return templateContext;
  }

  @Override
  public final Object[] getArguments() {
    if (templateContext == null) {
      throw new IllegalStateException("cannot get arguments unless a template context exists");
    }
    return args;
  }

  @Override
  public final Object getLiteralArgument() {
    if (templateContext != null) {
      throw new IllegalStateException("cannot get literal argument if a template context exists");
    }
    return args[0];
  }

  @Override
  public final boolean wasForced() {
    // Check explicit TRUE here because findValue() can return null (which would fail unboxing).
    return metadata != null && Boolean.TRUE.equals(metadata.findValue(Key.WAS_FORCED));
  }

  /**
   * Returns any additional metadata for this log statement.
   *
   * <p>When called outside of the logging backend, this method may return different values at
   * different times (ie, it may initially return a shared static "empty" metadata object and later
   * return a different implementation). As such it is not safe to cache the instance returned by
   * this method or to attempt to cast it to any particular implementation.
   */
  @Override
  public final Metadata getMetadata() {
    return metadata != null ? metadata : Metadata.empty();
  }

  // ---- Mutable Metadata ----

  /**
   * Adds the given key/value pair to this logging context. If the key cannot be repeated, and there
   * is already a value for the key in the metadata, then the existing value is replaced, otherwise
   * the value is added at the end of the metadata.
   *
   * @param key the metadata key (see {@link LogData}).
   * @param value the metadata value.
   */
  protected final <T> void addMetadata(MetadataKey<T> key, T value) {
    if (metadata == null) {
      metadata = new MutableMetadata();
    }
    metadata.addValue(key, value);
  }

  /**
   * Removes all key/value pairs with the specified key. Note that this method does not resize any
   * underlying backing arrays or other storage as logging contexts are expected to be short lived.
   *
   * @param key the metadata key (see {@link LogData}).
   */
  protected final void removeMetadata(MetadataKey<?> key) {
    if (metadata != null) {
      metadata.removeAllValues(key);
    }
  }

  // ---- Post processing ----

  /**
   * A callback which can be overridden to implement post processing of logging contexts prior to
   * passing them to the backend.
   *
   * <h2>Basic Responsibilities</h2>
   *
   * <p>This method is responsible for:
   *
   * <ol>
   *   <li>Performing any rate limiting operations specific to the extended API.
   *   <li>Updating per log-site information (e.g. for debug metrics).
   *   <li>Adding any additional metadata to this context.
   *   <li>Returning whether logging should be attempted.
   * </ol>
   *
   * <p>Implementations of this method must always call {@code super.postProcess()} first with the
   * given log site key:
   *
   * <pre>{@code protected boolean postProcess(@NullableDecl LogSiteKey logSiteKey) {
   *   boolean shouldLog = super.postProcess(logSiteKey);
   *   // Handle rate limiting if present.
   *   // Add additional metadata etc.
   *   return shouldLog;
   * }}</pre>
   *
   * <h2>Log Site Keys</h2>
   *
   * <p>If per log-site information is needed during post-processing, it should be stored using a
   * {@link LogSiteMap}. This will correctly handle "specialized" log-site keys and remove the risk
   * of memory leaks due to retaining unused log site data indefinitely.
   *
   * <p>Note that the given {@code logSiteKey} can be more specific than the {@link LogSite} of a
   * log statement (i.e. a single log statement can have multiple distinct versions of its state).
   * See {@link #per(Enum)} for more information.
   *
   * <p>If a log statement cannot be identified uniquely, then {@code logSiteKey} will be {@code
   * null}, and this method must behave exactly as if the corresponding fluent method had not been
   * invoked. On a system in which log site information is <em>unavailable</em>:
   *
   * <pre>{@code logger.atInfo().every(100).withCause(e).log("Some message"); }</pre>
   *
   * should behave exactly the same as:
   *
   * <pre>{@code logger.atInfo().withCause(e).log("Some message"); }</pre>
   *
   * <h2>Rate Limiting and Skipped Logs</h2>
   *
   * <p>When handling rate limiting, {@link #updateRateLimiterStatus(RateLimitStatus)} should be
   * called for each active rate limiter. This ensures that even if logging does not occur, the
   * number of "skipped" log statements is recorded correctly and emitted for the next allowed log.
   *
   * <p>If {@code postProcess()} returns {@code false} without updating the rate limit status, the
   * log statement may not be counted as skipped. In some situations this is desired, but either way
   * the extended logging API should make it clear to the user (via documentation) what will happen.
   * However in most cases {@code postProcess()} is only expected to return {@code false} due to
   * rate limiting.
   *
   * <p>If rate limiters are used there are still situations in which {@code postProcess()} can
   * return {@code true}, but logging will not occur. This is due to race conditions around the
   * resetting of rate limiter state. A {@code postProcess()} method can "early exit" as soon as
   * {@code shouldLog} is false, but should assume logging will occur while it remains {@code true}.
   *
   * <p>If a method in the logging chain determines that logging should definitely not occur, it may
   * choose to return the {@code NoOp} logging API at that point. However this will bypass any
   * post-processing, and no rate limiter state will be updated. This is sometimes desirable, but
   * the API documentation should make it clear to the user as to which behaviour occurs.
   *
   * <p>For example, level selector methods (such as {@code atInfo()}) return the {@code NoOp} API
   * for "disabled" log statements, and these have no effect on rate limiter state, and will not
   * update the "skipped" count. This is fine because controlling logging via log level selection is
   * not conceptually a form of "rate limiting".
   *
   * <p>The default implementation of this method enforces the rate limits as set by {@link
   * #every(int)} and {@link #atMostEvery(int, TimeUnit)}.
   *
   * @param logSiteKey used to lookup persistent, per log statement, state.
   * @return true if logging should be attempted (usually based on rate limiter state).
   */
  protected boolean postProcess(@NullableDecl LogSiteKey logSiteKey) {
    // Without metadata there's nothing to post-process.
    if (metadata != null) {
      // Without a log site we ignore any log-site specific behaviour.
      if (logSiteKey != null) {
        // Since the base class postProcess() should be invoked before subclass logic, we can set
        // the initial status here. Subclasses can combine this with other rate limiter statuses by
        // calling updateRateLimiterStatus() before we get back into shouldLog().
        RateLimitStatus status = DurationRateLimiter.check(metadata, logSiteKey, timestampNanos);
        status = RateLimitStatus.combine(status, CountingRateLimiter.check(metadata, logSiteKey));
        status = RateLimitStatus.combine(status, SamplingRateLimiter.check(metadata, logSiteKey));
        this.rateLimitStatus = status;

        // Early exit as soon as we know the log statement is disallowed. A subclass may still do
        // post processing but should never re-enable the log.
        if (status == RateLimitStatus.DISALLOW) {
          return false;
        }
      }

      // This does not affect whether logging will occur, only what additional data it contains.
      StackSize stackSize = metadata.findValue(Key.CONTEXT_STACK_SIZE);
      if (stackSize != null) {
        // We add this information to the stack trace exception so it doesn't need to go here.
        removeMetadata(Key.CONTEXT_STACK_SIZE);
        // IMPORTANT: Skipping at least 1 stack frame below is essential for correctness, since
        // postProcess() can be overridden, so the stack could look like:
        //
        // ^  UserCode::someMethod       << we want to start here and skip everything below
        // |  LogContext::log
        // |  LogContext::shouldLog
        // |  OtherChildContext::postProcess
        // |  ChildContext::postProcess  << this is *not* the caller of LogContext we're after
        // \- LogContext::postProcess    << we are here
        //
        // By skipping the initial code inside this method, we don't trigger any stack capture until
        // after the "log" method.
        LogSiteStackTrace context =
            new LogSiteStackTrace(
                getMetadata().findValue(Key.LOG_CAUSE),
                stackSize,
                getStackForCallerOf(LogContext.class, stackSize.getMaxDepth(), 1));
        // The "cause" is a unique metadata key, we must replace any existing value.
        addMetadata(Key.LOG_CAUSE, context);
      }
    }
    // By default, no restrictions apply so we should log.
    return true;
  }

  /**
   * Callback to allow custom log contexts to apply additional rate limiting behaviour. This should
   * be called from within an overriden {@code postProcess()} method. Typically this is invoked
   * after calling {@code super.postProcess(logSiteKey)}, such as:
   *
   * <pre>{@code protected boolean postProcess(@NullableDecl LogSiteKey logSiteKey) {
   *   boolean shouldLog = super.postProcess(logSiteKey);
   *   // Even if `shouldLog` is false, we still call the rate limiter to update its state.
   *   shouldLog &= updateRateLimiterStatus(CustomRateLimiter.check(...));
   *   if (shouldLog) {
   *     // Maybe add additional metadata here...
   *   }
   *   return shouldLog;
   * }}</pre>
   *
   * <p>See {@link RateLimitStatus} for more information on how to implement custom rate limiting in
   * Flogger.
   *
   * @param status a rate limiting status, or {@code null} if the rate limiter was not active.
   * @return whether logging will occur based on the current combined state of active rate limiters.
   */
  protected final boolean updateRateLimiterStatus(@NullableDecl RateLimitStatus status) {
    rateLimitStatus = RateLimitStatus.combine(rateLimitStatus, status);
    return rateLimitStatus != RateLimitStatus.DISALLOW;
  }

  /**
   * Pre-processes log metadata and determines whether we should make the pending logging call.
   *
   * <p>Note that this call is made inside each of the individual log methods (rather than in {@code
   * logImpl()}) because it is better to decide whether we are actually going to do the logging
   * before we pay the price of creating a varargs array and doing things like auto-boxing of
   * arguments.
   */
  private boolean shouldLog() {
    // The log site may have already been injected via "withInjectedLogSite()" or similar.
    if (logSite == null) {
      // From the point at which we call inferLogSite() we can skip 1 additional method (the
      // shouldLog() method itself) when looking up the stack to find the log() method.
      logSite =
          checkNotNull(
              Platform.getCallerFinder().findLogSite(LogContext.class, 1),
              "logger backend must not return a null LogSite");
    }
    LogSiteKey logSiteKey = null;
    if (logSite != LogSite.INVALID) {
      logSiteKey = logSite;
      // Log site keys are only modified when we have metadata in the log statement.
      if (metadata != null && metadata.size() > 0) {
        logSiteKey = specializeLogSiteKeyFromMetadata(logSiteKey, metadata);
      }
    }
    boolean shouldLog = postProcess(logSiteKey);
    if (rateLimitStatus != null) {
      // We check rate limit status even if it is "DISALLOW" to update the skipped logs count.
      int skippedLogs = RateLimitStatus.checkStatus(rateLimitStatus, logSiteKey, metadata);
      if (shouldLog && skippedLogs > 0) {
        metadata.addValue(Key.SKIPPED_LOG_COUNT, skippedLogs);
      }
      // checkStatus() returns -1 in two cases:
      // 1. We passed it the DISALLOW status.
      // 2. We passed it an "allow" status, but multiple threads were racing to try and reset the
      //    rate limiters, and this thread lost.
      // Either way we should suppress logging.
      shouldLog &= (skippedLogs >= 0);
    }
    return shouldLog;
  }

  // WARNING: If we ever start to use combined log-site and scoped context metadata here via
  // MetadataProcessor, there's an issue. It's possible that the same scope can appear in both
  // the context and the log-site, and multiplicity should not matter; BUT IT DOES! This means
  // that a log statement executed both in and outside of the context would currently see
  // different keys, when they should be the same. To fix this, specialization must be changed
  // to ignore repeated scopes. For now we only see log site metadata so this is not an issue.
  //
  // TODO: Ignore repeated scopes (e.g. use a Bloom Filter mask on each scope).
  // TODO: Make a proper iterator on Metadata or use MetadataProcessor.
  //
  // Visible for testing
  static LogSiteKey specializeLogSiteKeyFromMetadata(LogSiteKey logSiteKey, Metadata metadata) {
    checkNotNull(logSiteKey, "logSiteKey"); // For package null checker only.
    for (int n = 0, size = metadata.size(); n < size; n++) {
      if (Key.LOG_SITE_GROUPING_KEY.equals(metadata.getKey(n))) {
        Object groupByQualifier = metadata.getValue(n);
        // Logging scopes need special treatment to handle tidying up when closed.
        if (groupByQualifier instanceof LoggingScope) {
          logSiteKey = ((LoggingScope) groupByQualifier).specialize(logSiteKey);
        } else {
          logSiteKey = SpecializedLogSiteKey.of(logSiteKey, groupByQualifier);
        }
      }
    }
    return logSiteKey;
  }

  /**
   * Make the backend logging call. This is the point at which we have paid the price of creating a
   * varargs array and doing any necessary auto-boxing.
   */
  @SuppressWarnings("ReferenceEquality")
  private void logImpl(String message, Object... args) {
    this.args = args;
    // Evaluate any (rare) LazyArg instances early. This may throw exceptions from user code, but
    // it seems reasonable to propagate them in this case (they would have been thrown if the
    // argument was evaluated at the call site anyway).
    for (int n = 0; n < args.length; n++) {
      if (args[n] instanceof LazyArg) {
        args[n] = ((LazyArg<?>) args[n]).evaluate();
      }
    }
    // Using "!=" is fast and sufficient here because the only real case this should be skipping
    // is when we called log(String) or log(), which should not result in a template being created.
    // DO NOT replace this with a string instance which can be interned, or use equals() here,
    // since that could mistakenly treat other calls to log(String, Object...) incorrectly.
    if (message != LITERAL_VALUE_MESSAGE) {
      this.templateContext = new TemplateContext(getMessageParser(), message);
    }
    // Right at the end of processing add any tags injected by the platform. Any tags supplied at
    // the log site are merged with the injected tags (though this should be very rare).
    Tags tags = Platform.getInjectedTags();
    if (!tags.isEmpty()) {
      Tags logSiteTags = getMetadata().findValue(Key.TAGS);
      if (logSiteTags != null) {
        tags = tags.merge(logSiteTags);
      }
      addMetadata(Key.TAGS, tags);
    }
    // Pass the completed log data to the backend (it should not be modified after this point).
    getLogger().write(this);
  }

  // ---- Log site injection (used by pre-processors and special cases) ----

  @Override
  public final API withInjectedLogSite(LogSite logSite) {
    // First call wins (since auto-injection will typically target the log() method at the end of
    // the chain and might not check for previous explicit injection). In particular it MUST be
    // allowed for a caller to specify the "INVALID" log site, and have that set the field here to
    // disable log site lookup at this log statement (though passing "null" is a no-op).
    if (this.logSite == null && logSite != null) {
      this.logSite = logSite;
    }
    return api();
  }

  @SuppressWarnings("deprecation")
  @Override
  public final API withInjectedLogSite(
      String internalClassName,
      String methodName,
      int encodedLineNumber,
      @NullableDecl String sourceFileName) {
    return withInjectedLogSite(
        LogSite.injectedLogSite(internalClassName, methodName, encodedLineNumber, sourceFileName));
  }

  // ---- Public logging API ----

  @Override
  public final boolean isEnabled() {
    // We can't guarantee that all logger implementations will return instances of this class
    // _only_ when logging is enabled, so if would be potentially unsafe to just return true here.
    // It's not worth caching this result in the instance because calls to this method should be
    // rare and they are only going to be made once per instance anyway.
    return wasForced() || getLogger().isLoggable(level);
  }

  @Override
  public final <T> API with(MetadataKey<T> key, @NullableDecl T value) {
    // Null keys are always bad (even if the value is also null). This is one of the few places
    // where the logger API will throw a runtime exception (and as such it's important to ensure
    // the NoOp implementation also does the check). The reasoning for this is that the metadata
    // key is never expected to be passed user data, and should always be a static constant.
    // Because of this it's always going to be an obvious code error if we get a null here.
    checkNotNull(key, "metadata key");
    if (value != null) {
      addMetadata(key, value);
    }
    return api();
  }

  @Override
  public final API with(MetadataKey<Boolean> key) {
    return with(key, Boolean.TRUE);
  }

  @Override
  public <T> API per(@NullableDecl T key, LogPerBucketingStrategy<? super T> strategy) {
    // Skip calling the bucketer for null so implementations don't need to check.
    return key != null ? with(Key.LOG_SITE_GROUPING_KEY, strategy.apply(key)) : api();
  }

  @Override
  public final API per(Enum<?> key) {
    return with(Key.LOG_SITE_GROUPING_KEY, key);
  }

  @Override
  public API per(LoggingScopeProvider scopeProvider) {
    return with(Key.LOG_SITE_GROUPING_KEY, scopeProvider.getCurrentScope());
  }

  @Override
  public final API withCause(Throwable cause) {
    return with(Key.LOG_CAUSE, cause);
  }

  @Override
  public API withStackTrace(StackSize size) {
    // Unlike other metadata methods, the "no-op" value is not null.
    if (checkNotNull(size, "stack size") != StackSize.NONE) {
      addMetadata(Key.CONTEXT_STACK_SIZE, size);
    }
    return api();
  }

  @Override
  public final API every(int n) {
    return everyImpl(Key.LOG_EVERY_N, n, "rate limit");
  }

  @Override
  public final API onAverageEvery(int n) {
    return everyImpl(Key.LOG_SAMPLE_EVERY_N, n, "sampling");
  }

  private API everyImpl(MetadataKey<Integer> key, int n, String label) {
    // See wasForced() for discussion as to why this occurs before argument checking.
    if (wasForced()) {
      return api();
    }
    if (n <= 0) {
      throw new IllegalArgumentException(label + " count must be positive");
    }
    // 1-in-1 rate limiting is a no-op.
    if (n > 1) {
      addMetadata(key, n);
    }
    return api();
  }

  @Override
  public final API atMostEvery(int n, TimeUnit unit) {
    // See wasForced() for discussion as to why this occurs before argument checking.
    if (wasForced()) {
      return api();
    }
    if (n < 0) {
      throw new IllegalArgumentException("rate limit period cannot be negative");
    }
    // Rate limiting with a zero length period is a no-op, but if the time unit is nanoseconds then
    // the value is rounded up inside the rate limit object.
    if (n > 0) {
      addMetadata(Key.LOG_AT_MOST_EVERY, DurationRateLimiter.newRateLimitPeriod(n, unit));
    }
    return api();
  }

  /*
   * Note that while all log statements look almost identical to each other, it is vital that we
   * keep the 'shouldLog()' call outside of the call to 'logImpl()' so we can decide whether or not
   * to abort logging before we do any varargs creation.
   */

  @Override
  public final void log() {
    if (shouldLog()) {
      logImpl(LITERAL_VALUE_MESSAGE, "");
    }
  }

  @Override
  public final void log(String msg) {
    if (shouldLog()) {
      logImpl(LITERAL_VALUE_MESSAGE, msg);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1) {
    if (shouldLog()) {
      logImpl(message, p1);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(
      String message, @NullableDecl Object p1, @NullableDecl Object p2, @NullableDecl Object p3) {
    if (shouldLog()) {
      logImpl(message, p1, p2, p3);
    }
  }

  @Override
  public final void log(
      String message,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4) {
    if (shouldLog()) {
      logImpl(message, p1, p2, p3, p4);
    }
  }

  @Override
  public final void log(
      String msg,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4,
      @NullableDecl Object p5) {
    if (shouldLog()) {
      logImpl(msg, p1, p2, p3, p4, p5);
    }
  }

  @Override
  public final void log(
      String msg,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4,
      @NullableDecl Object p5,
      @NullableDecl Object p6) {
    if (shouldLog()) {
      logImpl(msg, p1, p2, p3, p4, p5, p6);
    }
  }

  @Override
  public final void log(
      String msg,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4,
      @NullableDecl Object p5,
      @NullableDecl Object p6,
      @NullableDecl Object p7) {
    if (shouldLog()) {
      logImpl(msg, p1, p2, p3, p4, p5, p6, p7);
    }
  }

  @Override
  public final void log(
      String msg,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4,
      @NullableDecl Object p5,
      @NullableDecl Object p6,
      @NullableDecl Object p7,
      @NullableDecl Object p8) {
    if (shouldLog()) {
      logImpl(msg, p1, p2, p3, p4, p5, p6, p7, p8);
    }
  }

  @Override
  public final void log(
      String msg,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4,
      @NullableDecl Object p5,
      @NullableDecl Object p6,
      @NullableDecl Object p7,
      @NullableDecl Object p8,
      @NullableDecl Object p9) {
    if (shouldLog()) {
      logImpl(msg, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }
  }

  @Override
  public final void log(
      String msg,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4,
      @NullableDecl Object p5,
      @NullableDecl Object p6,
      @NullableDecl Object p7,
      @NullableDecl Object p8,
      @NullableDecl Object p9,
      @NullableDecl Object p10) {
    if (shouldLog()) {
      logImpl(msg, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
    }
  }

  @Override
  public final void log(
      String msg,
      @NullableDecl Object p1,
      @NullableDecl Object p2,
      @NullableDecl Object p3,
      @NullableDecl Object p4,
      @NullableDecl Object p5,
      @NullableDecl Object p6,
      @NullableDecl Object p7,
      @NullableDecl Object p8,
      @NullableDecl Object p9,
      @NullableDecl Object p10,
      Object... rest) {
    if (shouldLog()) {
      // Manually create a new varargs array and copy the parameters in.
      Object[] params = new Object[rest.length + 10];
      params[0] = p1;
      params[1] = p2;
      params[2] = p3;
      params[3] = p4;
      params[4] = p5;
      params[5] = p6;
      params[6] = p7;
      params[7] = p8;
      params[8] = p9;
      params[9] = p10;
      System.arraycopy(rest, 0, params, 10, rest.length);
      logImpl(msg, params);
    }
  }

  @Override
  public final void log(String message, char p1) {
    if (shouldLog()) {
      logImpl(message, p1);
    }
  }

  @Override
  public final void log(String message, byte p1) {
    if (shouldLog()) {
      logImpl(message, p1);
    }
  }

  @Override
  public final void log(String message, short p1) {
    if (shouldLog()) {
      logImpl(message, p1);
    }
  }

  @Override
  public final void log(String message, int p1) {
    if (shouldLog()) {
      logImpl(message, p1);
    }
  }

  @Override
  public final void log(String message, long p1) {
    if (shouldLog()) {
      logImpl(message, p1);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, @NullableDecl Object p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, @NullableDecl Object p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, boolean p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, char p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, byte p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, short p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, int p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, long p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, float p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, boolean p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, char p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, byte p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, short p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, int p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, long p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, float p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void log(String message, double p1, double p2) {
    if (shouldLog()) {
      logImpl(message, p1, p2);
    }
  }

  @Override
  public final void logVarargs(String message, @NullableDecl Object[] params) {
    if (shouldLog()) {
      // Copy the varargs array (because we didn't create it and this is quite a rare case).
      logImpl(message, Arrays.copyOf(params, params.length));
    }
  }
}
