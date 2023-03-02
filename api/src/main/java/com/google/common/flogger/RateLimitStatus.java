/*
 * Copyright (C) 2023 The Flogger Authors.
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

import com.google.common.flogger.backend.Metadata;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Status for rate limiting operations, usable by rate limiters and available to subclasses of
 * {@code LogContext} to handle rate limiting consistently.
 *
 * <h2>Design Notes</h2>
 *
 * <p>The purpose of this class is to allow rate limiters to behave in a way which is consistent
 * when multiple rate limiters are combined for a single log statement. If you are writing a rate
 * limiter for Flogger which you want to "play well" with other rate limiters, it is essential that
 * you understand how {@code RateLimitStatus} is designed to work.
 *
 * <p>Firstly, {@code LogContext} tracks a single status for each log statement reached. This is
 * modified by code in the {@code postProcess()} method (which can be overridden by custom logger
 * implementations).
 *
 * <p>When a rate limiter is used, it returns a {@code RateLimitStatus}, which is combined with the
 * existing value held in the context:
 *
 * <pre>{@code
 * rateLimitStatus = RateLimitStatus.combine(rateLimitStatus, MyCustomRateLimiter.check(...));
 * }>/pre>
 *
 * <p>A rate limiter should switch between two primary states "limiting" and "pending":
 * <ul>
 * <li>In the "limiting" state, the limiter should return the {@link RateLimitStatus#DISALLOW} value
 * and update any internal state until it reaches its trigger condition. Once the trigger condition
 * is reached, the limiter enters the "pending" state.
 * <li>In the "pending" state, the limiter returns an "allow" status <em>until it is
 * {@link RateLimitStatus#reset()}</em>.
 * </ul>
 *
 * <p>This two-step approach means that, when multiple rate limiters are active for a single log
 * statement, logging occurs after all rate limiters are "pending" (and at this point they are all
 * reset). This is much more consistent than having each rate limiter operate independently, and
 * allows a much more intuitive understanding of expected behaviour.
 *
 * <p>It is recommended that most rate limiters should start in the "pending" state to ensure that
 * the first log statement they process is emitted (even when multiple rate limiters are used). This
 * isn't required, but it should be documented either way.
 *
 * <p>Each rate limiter is expected to follow this basic structure:
 *
 * <pre>{@code
 * final class CustomRateLimiter extends RateLimitStatus {
 *   private static final LogSiteMap<CustomRateLimiter> map =
 *       new LogSiteMap<CustomRateLimiter>() {
 *         @Override protected CustomRateLimiter initialValue() {
 *           return new CustomRateLimiter();
 *         }
 *       };
 *
 *  static RateLimitStatus check(Metadata metadata, LogSiteKey logSiteKey, ...) {
 *    MyRateLimitData rateLimitData = metadata.findValue(MY_CUSTOM_KEY);
 *    if (rateLimitData == null) {
 *      return null;
 *    }
 *    return map.get(logSiteKey, metadata).checkRateLimit(rateLimitData, ...);
 *  }
 *
 *  RateLimitStatus checkRateLimit(MyRateLimitData rateLimitData, ...) {
 *    <update internal state>
 *    return <is-pending> ? this : DISALLOW;
 *  }
 *
 *   @Override
 *   public void reset() {
 *     <reset from "pending" to "limiting" state>
 *   }
 * }
 * }>/pre>
 *
 * <p>The use of {@code LogLevelMap} ensures a rate limiter instance is held separately for each log
 * statement, but it also handles complex garbage collection issues around "specialized" log site
 * keys. All rate limiter implementations <em>MUST</em> use this approach.
 *
 * <p>Having the rate limiter class extend {@code RateLimitStatus} is a convenience for the case
 * where the {@code reset()} operation requires no additional information. If the {@code reset()}
 * operation requires extra state (e.g. from previous logging calls) then this approach will not be
 * possible, and a separate {@code RateLimitStatus} subclass would need to be allocated to hold that
 * state.
 *
 * <p>Rate limiter instances <em>MUST</em> be thread safe, and should avoid using locks wherever
 * possible (since using explicit locking can cause unacceptable thread contention in highly
 * concurrent systems).
 */
public abstract class RateLimitStatus {
  /**
   * The status to return whenever a rate limiter determines that logging should not occur.
   *
   * <p>All other statuses implicity "allow" logging.
   */
  public static final RateLimitStatus DISALLOW = sentinel();

  /**
   * The status to return whenever a stateless rate limiter determines that logging should occur.
   *
   * <p>Note: Truly stateless rate limiters should be <em>very</em> rare, since they cannot hold
   * onto a pending "allow" state. Even a simple "sampling rate limiter" should be stateful if once
   * the "allow" state is reached it continues to be returned until logging actually occurs.
   */
  public static final RateLimitStatus ALLOW = sentinel();

  private static RateLimitStatus sentinel() {
    return new RateLimitStatus() {
      @Override
      public void reset() {}
    };
  }

  /**
   * A log guard ensures that only one thread can claim "logging rights" for a log statement once an
   * "allow" rate limit status is set. It also tracks the number of skipped invocations of the log
   * site key.
   * 
   * <p>Note that the skipped count is tracked via the "log site key" and there may be several keys
   * for a single log site (e.g. due to use of the {@code per(...)} methods). This is consistent
   * with everywhere else which handles log site specific state, but does make it a little less
   * obvious what the skipped count refers to at first glance.
   */
  private static final class LogGuard {
    private static final LogSiteMap<LogGuard> guardMap =
        new LogSiteMap<LogGuard>() {
          @Override
          public LogGuard initialValue() {
            return new LogGuard();
          }
        };

    static int checkAndGetSkippedCount(
        RateLimitStatus status, LogSiteKey logSiteKey, Metadata metadata) {
      LogGuard guard = guardMap.get(logSiteKey, metadata);
      // Pre-increment pendingCount to include this log statement, so (pendingCount > 0).
      int pendingCount = guard.pendingLogCount.incrementAndGet();
      if (status == DISALLOW || !guard.shouldReset.compareAndSet(false, true)) {
        return -1;
      }
      // Logging is allowed, and this thread has claimed the right to do it.
      try {
        status.reset();
      } finally {
        guard.shouldReset.set(false);
      }
      // Subtract the pending count (this might not go to zero if other threads are incrementing).
      guard.pendingLogCount.addAndGet(-pendingCount);
      // Return the skipped log count (which must be >= 0).
      return pendingCount - 1;
    }

    private final AtomicBoolean shouldReset = new AtomicBoolean();
    private final AtomicInteger pendingLogCount = new AtomicInteger();
  }

  /**
   * The rules for combining statuses are (in order):
   *
   * <ul>
   *   <li>If either value is {@code null}, the other value is returned (possibly {@code null}).
   *   <li>If either value is {@code ALLOW} (the constant), the other non-null value is returned.
   *   <li>If either value is {@code DISALLOW}, {@code DISALLOW} is returned.
   *   <li>Otherwise a combined status is returned from the two non-null "allow" statuses.
   * </ul>
   *
   * <p>In {@link LogContext} the {@code rateLimitStatus} field is set to the combined value of all
   * rate limiter statuses.
   *
   * <p>This ensures that after rate limit processing:
   *
   * <ol>
   *   <li>If {@code rateLimitStatus == null} no rate limiters were applied, so logging is allowed.
   *   <li>If {@code rateLimitStatus == DISALLOW}, the log was suppressed by rate limiting.
   *   <li>Otherwise the log statement was allowed, but rate limiters must now be reset.
   * </ol>
   *
   * <p>This code ensures that in the normal case of having no rate limiting for a log statement, no
   * allocations occur. It also ensures that (assuming well written rate limiters) there are no
   * allocations for log statements using a single rate limiter.
   */
  @NullableDecl
  static RateLimitStatus combine(
      @NullableDecl final RateLimitStatus a, @NullableDecl final RateLimitStatus b) {
    // In the vast majority of cases this code will be run once per log statement, and at least one
    // of 'a' or 'b' will be null. So optimize early exiting for that case.
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    // This is already a rare situation where 2 rate limiters are active for the same log statement.
    // However in most of these cases, at least one will likley "disallow" logging.
    if (a == DISALLOW || b == ALLOW) {
      return a;
    }
    if (b == DISALLOW || a == ALLOW) {
      return b;
    }
    // Getting here should be very rare and happens only when multiple rate limiters have reached
    // the "pending" state and logging should occur. Neither status is null, ALLOW or DISALLOW.
    return new RateLimitStatus() {
      @Override
      public void reset() {
        // Make sure both statuses are reset regardless of errors. If both throw errors we only
        // expose the 2nd one (we don't track "suppressed" exceptions). This is fine though since
        // a reset() method should never risk throwing anything in the first place.
        try {
          a.reset();
        } finally {
          b.reset();
        }
      }
    };
  }

  /**
   * Checks rate limiter status and returns either the number of skipped log statements for the
   * {@code logSiteKey} (indicating that this log statement should be emitted) or {@code -1} if it
   * should be skipped.
   */
  static int checkStatus(RateLimitStatus status, LogSiteKey logSiteKey, Metadata metadata) {
    return LogGuard.checkAndGetSkippedCount(status, logSiteKey, metadata);
  }

  /**
   * Rate limiters can extend this class directly if their "reset" operation is stateless, or they
   * can create and return new instances to capture any necessary state.
   */
  protected RateLimitStatus() {}

  /**
   * Resets an associated rate limiter, moving it out of the "pending" state and back into rate
   * limiting mode.
   * 
   * <p>Note: This method is never invoked concurrently with another {@code reset()} operation, but
   * it can be concurrent with calls to update rate limiter state. Thus it must be thread safe in
   * general, but can assume it's the only reset operation active for the limiter which returned it.
   */
  protected abstract void reset();
}
