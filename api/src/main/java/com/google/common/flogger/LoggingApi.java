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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * The basic logging API. An implementation of this API (or an extension of it) will be
 * returned by any fluent logger, and forms the basis of the fluent call chain.
 * <p>
 * In typical usage each method in the API, with the exception of the terminal {@code log()}
 * statements, will carry out some simple task (which may involve modifying the context of the log
 * statement) and return the same API for chaining. The exceptions to this are:
 * <ul>
 * <li>Methods which return a NoOp implementation of the API in order to disable logging.
 * <li>Methods which return an alternate API in order to implement context specific grammar (though
 *     these alternate APIs should always return the original logging API eventually).
 * </ul>
 * A hypothetical example of a context specific grammar might be:
 * <pre>{@code
 * logger.at(WARNING).whenSystem().isLowOnMemory().log("");
 * }</pre>
 * In this example the {@code whenSystem()} method would return its own API with several context
 * specific methods ({@code isLowOnMemory()}, {@code isThrashing()} etc...), however each of these
 * sub-APIs must eventually return the original logging API.
 */
// NOTE: new methods to this interface should be coordinated with google-java-format
@CheckReturnValue
public interface LoggingApi<API extends LoggingApi<API>> {
  /**
   * Associates a {@link Throwable} instance with the current log statement, to be interpreted as
   * the cause of this statement. Typically this method will be used from within catch blocks to log
   * the caught exception or error. If the cause is {@code null} then this method has no effect.
   * <p>
   * If this method is called multiple times for a single log statement, the last invocation will
   * take precedence.
   */
  API withCause(@Nullable Throwable cause);

  /**
   * Modifies the current log statement to be emitted only one-in-N times that it is invoked. The
   * specified count must be greater than zero and it is expected, but not required, that it is
   * constant. The first invocation of any rate-limited log statement will always be emitted.
   * <p>
   * Note also that if {@code every()} and {@link #atMostEvery(int, TimeUnit)} are invoked for the
   * same log statement, then the log statement will be emitted when both criteria are satisfied.
   * <p>
   * If this method is called multiple times for a single log statement, the last invocation will
   * take precedence.
   *
   * @param n the factor by which to reduce logging frequency.
   * @throws IllegalArgumentException if {@code n} is not positive.
   */
  API every(int n);

  /**
   * Modifies the current log statement to be emitted at most once per specified time period. The
   * specified duration must not be negative, and it is expected, but not required, that it is
   * constant. The first invocation of any rate-limited log statement will always be emitted.
   * <p>
   * Note that for performance reasons {@code atMostEvery()} is explicitly <em>not</em> intended to
   * perform "proper" rate limiting to produce a limited average rate over many samples.
   *
   * <h3>Behaviour</h3>
   *
   * A call to {@code atMostEvery()} will emit the current log statement if:
   * <pre>{@code
   *   currentTimestampNanos >= lastTimestampNanos + unit.toNanos(n)
   * }</pre>
   * where {@code currentTimestampNanos} is the timestamp of the current log statement and
   * {@code lastTimestampNanos} is a time stamp of the last log statement that was emitted.
   * <p>
   * The effect of this is that when logging invocation is relatively infrequent, the period
   * between emitted log statements can be higher than the specified duration. For example
   * if the following log statement were called every 600ms:
   * <pre>{@code
   *   logger.atFine().atMostEvery(2, SECONDS).log(...);
   * }</pre>
   * logging would occur after {@code 0s}, {@code 2.4s} and {@code 4.8s} (not {@code 4.2s}),
   * giving an effective duration of {@code 2.4s} between log statements over time.
   * <p>
   * Providing a zero length duration (ie, {@code n == 0}) disabled rate limiting and makes this
   * method an effective no-op.
   *
   * <h3>Granularity</h3>
   *
   * Because the implementation of this feature relies on a nanosecond timestamp provided by the
   * backend, the actual granularity of the underlying clock used may vary. Thus it is possible to
   * specify a time period smaller than the smallest visible time increment. If this occurs, then
   * the effective rate limit applied to the log statement will be the smallest available time
   * increment. For example, if the system clock granularity is 1 millisecond, and a
   * log statement is called with {@code atMostEvery(700, MICROSECONDS)}, the effective rate of
   * logging (even averaged over long periods) could never be more than once every millisecond.
   *
   * <h3>Notes</h3>
   *
   * Note that if {@code atMostEvery()} and {@link #every(int)} are invoked for the same log
   * statement, then the log statement will be emitted when both criteria are satisfied.
   * <p>
   * If this method is called multiple times for a single log statement, the last invocation will
   * take precedence.
   *
   * @param n the minimum number of time units between emitted log statements
   * @param unit the time unit for the duration
   * @throws IllegalArgumentException if {@code n} is negative.
   */
  API atMostEvery(int n, TimeUnit unit);

  /**
   * Creates a synthetic exception and attaches it as the "cause" of the log statement as a way to
   * provide additional context for the logging call itself. The exception created by this method is
   * always of the type {@link LogSiteStackTrace}, and its message indicates the stack size.
   *
   * <p>If the {@code withCause(e)} method is also called for the log statement (either before or
   * after) {@code withStackTrace()}, the given exception becomes the cause of the synthetic
   * exception.
   *
   * <p>Note that this method is experimental and may change in the future (using a "cause" to
   * provide additional debugging for normal log statements seems hacky and once ECatcher and other
   * tools can process Flogger's data in a more structured way, there should be no need to tunnel
   * the metadata via the cause).
   *
   * @param size the maximum size of the stack trace to be generated.
   */
  API withStackTrace(StackSize size);

  /**
   * Sets the log site for the current log statement. Explicit log site injection is very rarely
   * necessary, since either the log site is injected automatically, or it is determined at runtime
   * via stack analysis. The one use case where calling this method explicitly may be useful is
   * when making logging helper methods, where some common project specific logging behavior is
   * enshrined. For example, you can write:
   *
   * <pre> {@code
   * public void logStandardWarningAt(LogSite logSite, String message, Object... args) {
   *   logger.atWarning()
   *       .withInjectedLogSite(logSite)
   *       .atMostEvery(5, MINUTES)
   *       .logVarargs(message, args);
   * }
   * }</pre>
   *
   * and then code can do:
   * <pre> {@code
   * import static com.google.common.flogger.LogSites.logSite;
   * }</pre>
   * and elsewhere:
   * <pre> {@code
   * logStandardWarningAt(logSite(), "Badness");
   * ...
   * logStandardWarningAt(logSite(), "More badness: %s", getData());
   * }</pre>
   *
   * <p>Now each of the call sites for the helper method is treated as if it were in the logging
   * API, and things like rate limiting work separately for each, and the location in the log
   * statement will be the point at which the helper method was called.
   *
   * <p>It is very important to note that the {@code logSite()} call can be very slow, since
   * determining the log site can involve stack trace analysis. It is only recommended in cases
   * where logging is expected to occur (e.g. {@code WARNING} level or above). Luckily, there is
   * typically no need to implement helper methods for {@code FINE} logging, since it's usually
   * less structured and doesn't normally need to follow any specific "best practice" behavior.
   *
   * <p>Note however that any stack traces generated by {@link #withStackTrace(StackSize)} will
   * still contain the complete stack, including the call to the logger itself inside the helper
   * method.
   *
   * <p>This method must only be explicitly called once for any log statement, and if this method
   * is called multiple times the first invocation will take precedence. This is because log site
   * injection (if present) is expected to occur just before the final {@code log()} call and must
   * be overrideable by earlier (explicit) calls.
   *
   * @param logSite Log site which uniquely identifies any per-log statement resources.
   */
  API withInjectedLogSite(LogSite logSite);

  /**
   * Internal method not for public use. This method is only intended for use by the logger
   * agent and related classes and should never be invoked manually.
   *
   * @param internalClassName Slash separated class name obtained from the class constant pool.
   * @param methodName Method name obtained from the class constant pool.
   * @param encodedLineNumber line number and per-line log statement index encoded as a single
   *     32-bit value. The low 16-bits is the line number (0 to 0xFFFF inclusive) and the high
   *     16 bits is a log statement index to distinguish multiple statements on the same line
   *     (this becomes important if line numbers are stripped from the class file and everything
   *     appears to be on the same line).
   * @param sourceFileName Optional base name of the source file (this value is strictly for
   *     debugging and does not contribute to either equals() or hashCode() behavior).
   */
  API withInjectedLogSite(
      String internalClassName,
      String methodName,
      int encodedLineNumber,
      @Nullable String sourceFileName);

  /**
   * Returns true if logging is enabled at the level implied for this API, according to the current
   * logger backend. For example:
   * <pre>{@code
   *   if (logger.atFine().isEnabled()) {
   *     // Do non-trivial argument processing
   *     logger.atFine().log("Message: %s", value);
   *   }
   * }</pre>
   * <p>
   * Note that if logging is enabled for a log level, it does not always follow that the log
   * statement will definitely be written to the backend (due to the effects of other methods in
   * the fluent chain), but if this method returns {@code false} then it can safely be assumed that
   * no logging will occur.
   * <p>
   * This method is unaffected by additional methods in the fluent chain and should only ever be
   * invoked immediately after the level selector method. In other words, the expression:
   * <pre>{@code logger.atFine().every(100).isEnabled()}</pre>
   * is incorrect because it will always behave identically to:
   * <pre>{@code logger.atFine().isEnabled()}</pre>
   * <p>
   * <h3>Implementation Note</h3>
   * By avoiding passing a separate {@code Level} at runtime to determine "loggability", this API
   * makes it easier to coerce bytecode optimizers into doing "dead code" removal on sections
   * guarded by this method.
   * <p>
   * If a proxy logger class is supplied for which:
   * <pre>{@code logger.atFine()}</pre>
   * unconditionally returns the "NoOp" implementation of the API (in which {@code isEnabled()}
   * always returns {@code false}), it becomes simple for bytecode analysis to determine that:
   * <pre>{@code logger.atFine().isEnabled()}</pre>
   * always evaluates to {@code false} .
   */
  boolean isEnabled();

  /**
   * Terminal log statement when a message is not required. A {@code log} method must terminate all
   * fluent logging chains and the no-argument method can be used if there is no need for a log
   * message. For example:
   * <pre>{@code
   * logger.at(INFO).withCause(error).log();
   * }</pre>
   * <p>
   * However as it is good practice to give all log statements a meaningful log message, use of this
   * method should be rare.
   */
  void log();

  /**
   * Logs a formatted representation of values in the given array, using the specified message
   * template.
   * <p>
   * This method is only expected to be invoked with an existing varargs array passed in from
   * another method. Unlike {@link #log(String, Object)}, which would treat an array as a single
   * parameter, this method will unwrap the given array.
   *
   * @param message the message template string containing a single argument placeholder.
   * @param varargs the non-null array of arguments to be formatted.
   */
  void logVarargs(String message, Object[] varargs);

  /**
   * Logs the given literal string without without interpreting any argument placeholders.
   * <p>
   * Important: This is intended only for use with hard-coded, literal strings which cannot
   * contain user data. If you wish to log user generated data, you should do something like:
   * <pre>{@code
   * log("user data=%s", value);
   * }</pre>
   * This serves to give the user data context in the log file but, more importantly, makes it
   * clear which arguments may contain PII and other sensitive data (which might need to be
   * scrubbed during logging). This recommendation also applies to all the overloaded {@code log()}
   * methods below.
   */
  void log(String msg);

  // ---- Overloads for object arguments (to avoid vararg array creation). ----

  /**
   * Logs a formatted representation of the given parameter, using the specified message template.
   * The message string is expected to contain argument placeholder terms appropriate to the
   * logger's choice of parser.
   * <p>
   * Note that printf-style loggers are always expected to accept the standard Java printf
   * formatting characters (e.g. "%s", "%d" etc...) and all flags unless otherwise stated.
   * Null arguments are formatted as the literal string {@code "null"} regardless of
   * formatting flags.
   *
   * @param msg the message template string containing a single argument placeholder.
   */
  void log(String msg, @Nullable Object p1);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4,
      @Nullable Object p5);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4,
      @Nullable Object p5,
      @Nullable Object p6);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4,
      @Nullable Object p5,
      @Nullable Object p6,
      @Nullable Object p7);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4,
      @Nullable Object p5,
      @Nullable Object p6,
      @Nullable Object p7,
      @Nullable Object p8);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4,
      @Nullable Object p5,
      @Nullable Object p6,
      @Nullable Object p7,
      @Nullable Object p8,
      @Nullable Object p9);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4,
      @Nullable Object p5,
      @Nullable Object p6,
      @Nullable Object p7,
      @Nullable Object p8,
      @Nullable Object p9,
      @Nullable Object p10);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(
      String msg,
      @Nullable Object p1,
      @Nullable Object p2,
      @Nullable Object p3,
      @Nullable Object p4,
      @Nullable Object p5,
      @Nullable Object p6,
      @Nullable Object p7,
      @Nullable Object p8,
      @Nullable Object p9,
      @Nullable Object p10,
      Object... rest);

  // ---- Overloads for a single argument (to avoid auto-boxing and vararg array creation). ----

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1);

  // ---- Overloads for two arguments (to avoid auto-boxing and vararg array creation). ----
  /*
   * It may not be obvious why we need _all_ combinations of fundamental types here (because some
   * combinations should be rare enough that we can ignore them). However due to the precedence in
   * the Java compiler for converting fundamental types in preference to auto-boxing, and the need
   * to preserve information about the original type (byte, short, char etc...) when doing unsigned
   * formatting, it turns out that all combinations are required.
   */

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, @Nullable Object p1, double p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, @Nullable Object p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, @Nullable Object p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, boolean p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, boolean p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, char p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, char p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, byte p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, byte p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, short p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, short p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, int p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, int p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, long p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, long p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, float p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, float p2);

  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, boolean p1, double p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, char p1, double p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, byte p1, double p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, short p1, double p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, int p1, double p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, long p1, double p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, float p1, double p2);
  /** Logs a message with formatted arguments (see {@link #log(String, Object)} for details). */
  void log(String msg, double p1, double p2);

  /**
   * An implementation of {@link LoggingApi} which does nothing and discards all parameters.
   * <p>
   * This class (or a subclass in the case of an extended API) should be returned whenever logging
   * is definitely disabled (e.g. when the log level is too low).
   */
  public static class NoOp<API extends LoggingApi<API>> implements LoggingApi<API> {
    @SuppressWarnings("unchecked")
    protected final API noOp() {
      return (API) this;
    }

    @Override
    public API withInjectedLogSite(LogSite logSite) {
      checkNotNull(logSite, "log site");
      return noOp();
    }

    @Override
    public API withInjectedLogSite(
        String internalClassName,
        String methodName,
        int encodedLineNumber,
        @Nullable String sourceFileName) {
      return noOp();
    }

    @Override
    public final boolean isEnabled() {
      return false;
    }

    @Override
    public final API withCause(@Nullable Throwable cause) {
      return noOp();
    }

    @Override
    public final API every(int n) {
      return noOp();
    }

    @Override
    public final API atMostEvery(int n, TimeUnit unit) {
      checkNotNull(unit, "time unit");
      return noOp();
    }

    @Override
    public API withStackTrace(StackSize size) {
      // Don't permit null since NONE is the right thing to use.
      checkNotNull(size, "stack size");
      return noOp();
    }

    @Override
    public final void log() {}

    @Override
    public final void log(String msg) {}

    @Override
    public final void logVarargs(String msg, Object[] params) {}

    @Override
    public final void log(String msg, @Nullable Object p1) {}

    @Override
    public final void log(String msg, @Nullable Object p1, @Nullable Object p2) {}

    @Override
    public final void log(
        String msg, @Nullable Object p1, @Nullable Object p2, @Nullable Object p3) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4,
        @Nullable Object p5) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4,
        @Nullable Object p5,
        @Nullable Object p6) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4,
        @Nullable Object p5,
        @Nullable Object p6,
        @Nullable Object p7) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4,
        @Nullable Object p5,
        @Nullable Object p6,
        @Nullable Object p7,
        @Nullable Object p8) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4,
        @Nullable Object p5,
        @Nullable Object p6,
        @Nullable Object p7,
        @Nullable Object p8,
        @Nullable Object p9) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4,
        @Nullable Object p5,
        @Nullable Object p6,
        @Nullable Object p7,
        @Nullable Object p8,
        @Nullable Object p9,
        @Nullable Object p10) {}

    @Override
    public final void log(
        String msg,
        @Nullable Object p1,
        @Nullable Object p2,
        @Nullable Object p3,
        @Nullable Object p4,
        @Nullable Object p5,
        @Nullable Object p6,
        @Nullable Object p7,
        @Nullable Object p8,
        @Nullable Object p9,
        @Nullable Object p10,
        Object... rest) {}

    @Override
    public final void log(String msg, char p1) {}

    @Override
    public final void log(String msg, byte p1) {}

    @Override
    public final void log(String msg, short p1) {}

    @Override
    public final void log(String msg, int p1) {}

    @Override
    public final void log(String msg, long p1) {}

    @Override
    public final void log(String msg, @Nullable Object p1, boolean p2) {}

    @Override
    public final void log(String msg, @Nullable Object p1, char p2) {}

    @Override
    public final void log(String msg, @Nullable Object p1, byte p2) {}

    @Override
    public final void log(String msg, @Nullable Object p1, short p2) {}

    @Override
    public final void log(String msg, @Nullable Object p1, int p2) {}

    @Override
    public final void log(String msg, @Nullable Object p1, long p2) {}

    @Override
    public final void log(String msg, @Nullable Object p1, float p2) {}

    @Override
    public final void log(String msg, @Nullable Object p1, double p2) {}

    @Override
    public final void log(String msg, boolean p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, char p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, byte p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, short p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, int p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, long p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, float p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, double p1, @Nullable Object p2) {}

    @Override
    public final void log(String msg, boolean p1, boolean p2) {}

    @Override
    public final void log(String msg, char p1, boolean p2) {}

    @Override
    public final void log(String msg, byte p1, boolean p2) {}

    @Override
    public final void log(String msg, short p1, boolean p2) {}

    @Override
    public final void log(String msg, int p1, boolean p2) {}

    @Override
    public final void log(String msg, long p1, boolean p2) {}

    @Override
    public final void log(String msg, float p1, boolean p2) {}

    @Override
    public final void log(String msg, double p1, boolean p2) {}

    @Override
    public final void log(String msg, boolean p1, char p2) {}

    @Override
    public final void log(String msg, char p1, char p2) {}

    @Override
    public final void log(String msg, byte p1, char p2) {}

    @Override
    public final void log(String msg, short p1, char p2) {}

    @Override
    public final void log(String msg, int p1, char p2) {}

    @Override
    public final void log(String msg, long p1, char p2) {}

    @Override
    public final void log(String msg, float p1, char p2) {}

    @Override
    public final void log(String msg, double p1, char p2) {}

    @Override
    public final void log(String msg, boolean p1, byte p2) {}

    @Override
    public final void log(String msg, char p1, byte p2) {}

    @Override
    public final void log(String msg, byte p1, byte p2) {}

    @Override
    public final void log(String msg, short p1, byte p2) {}

    @Override
    public final void log(String msg, int p1, byte p2) {}

    @Override
    public final void log(String msg, long p1, byte p2) {}

    @Override
    public final void log(String msg, float p1, byte p2) {}

    @Override
    public final void log(String msg, double p1, byte p2) {}

    @Override
    public final void log(String msg, boolean p1, short p2) {}

    @Override
    public final void log(String msg, char p1, short p2) {}

    @Override
    public final void log(String msg, byte p1, short p2) {}

    @Override
    public final void log(String msg, short p1, short p2) {}

    @Override
    public final void log(String msg, int p1, short p2) {}

    @Override
    public final void log(String msg, long p1, short p2) {}

    @Override
    public final void log(String msg, float p1, short p2) {}

    @Override
    public final void log(String msg, double p1, short p2) {}

    @Override
    public final void log(String msg, boolean p1, int p2) {}

    @Override
    public final void log(String msg, char p1, int p2) {}

    @Override
    public final void log(String msg, byte p1, int p2) {}

    @Override
    public final void log(String msg, short p1, int p2) {}

    @Override
    public final void log(String msg, int p1, int p2) {}

    @Override
    public final void log(String msg, long p1, int p2) {}

    @Override
    public final void log(String msg, float p1, int p2) {}

    @Override
    public final void log(String msg, double p1, int p2) {}

    @Override
    public final void log(String msg, boolean p1, long p2) {}

    @Override
    public final void log(String msg, char p1, long p2) {}

    @Override
    public final void log(String msg, byte p1, long p2) {}

    @Override
    public final void log(String msg, short p1, long p2) {}

    @Override
    public final void log(String msg, int p1, long p2) {}

    @Override
    public final void log(String msg, long p1, long p2) {}

    @Override
    public final void log(String msg, float p1, long p2) {}

    @Override
    public final void log(String msg, double p1, long p2) {}

    @Override
    public final void log(String msg, boolean p1, float p2) {}

    @Override
    public final void log(String msg, char p1, float p2) {}

    @Override
    public final void log(String msg, byte p1, float p2) {}

    @Override
    public final void log(String msg, short p1, float p2) {}

    @Override
    public final void log(String msg, int p1, float p2) {}

    @Override
    public final void log(String msg, long p1, float p2) {}

    @Override
    public final void log(String msg, float p1, float p2) {}

    @Override
    public final void log(String msg, double p1, float p2) {}

    @Override
    public final void log(String msg, boolean p1, double p2) {}

    @Override
    public final void log(String msg, char p1, double p2) {}

    @Override
    public final void log(String msg, byte p1, double p2) {}

    @Override
    public final void log(String msg, short p1, double p2) {}

    @Override
    public final void log(String msg, int p1, double p2) {}

    @Override
    public final void log(String msg, long p1, double p2) {}

    @Override
    public final void log(String msg, float p1, double p2) {}

    @Override
    public final void log(String msg, double p1, double p2) {}
  }
}
