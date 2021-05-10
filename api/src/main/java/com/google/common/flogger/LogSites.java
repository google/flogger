/*
 * Copyright (C) 2018 The Flogger Authors.
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

import com.google.common.flogger.backend.Platform;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Helper class to generate log sites for the current line of code. This class is deliberately
 * isolated (rather than having the method in {@link LogSite} itself) because manual log site
 * injection is rare and by isolating it into a separate class may help encourage users to think
 * carefully about the issue.
 *
 */
public final class LogSites {
  /**
   * Returns a {@code LogSite} for the caller of the specified class. This can be used in
   * conjunction with the {@link LoggingApi#withInjectedLogSite(LogSite)} method to implement
   * logging helper methods. In some platforms, log site determination may be unsupported, and in
   * those cases this method will always return the {@link LogSite#INVALID} instance.
   * <p>
   * For example (in {@code MyLoggingHelper}):
   * <pre>{@code
   * public static void logAndSomethingElse(String message, Object... args) {
   *   logger.atInfo()
   *       .withInjectedLogSite(callerOf(MyLoggingHelper.class))
   *       .logVarargs(message, args);
   * }
   * }</pre>
   * <p>
   * This method should be used for the simple cases where the class in which the logging occurs is
   * a public logging API. If the log statement is in a different class (not the public logging API)
   * and the {@code LogSite} instance needs to be passed through several layers, consider using
   * {@link #logSite()} instead to avoid too much "magic" in your code.
   * <p>
   * You should also seek to ensure that any API used with this method "looks like a logging API".
   * It's no good if a log entry contains a class and method name which doesn't correspond to
   * anything the user can relate to. In particular, the API should probably always accept the log
   * message or at least some of its parameters, and should always have methods with "log" in their
   * names to make the connection clear.
   * <p>
   * It is very important to note that this method can be very slow, since determining the log site
   * can involve stack trace analysis. It is only recommended that it is used for cases where
   * logging is expected to occur (e.g. {@code INFO} level or above). Implementing a helper method
   * for {@code FINE} logging is usually unnecessary (it doesn't normally need to follow any
   * specific "best practice" behavior).
   * <p>
   * Note that even when log site determination is supported, it is not defined as to whether two
   * invocations of this method on the same line of code will produce the same instance, equivalent
   * instances or distinct instance. Thus you should never invoke this method twice in a single
   * statement (and you should never need to).
   * <p>
   * Note that this method call may be replaced in compiled applications via bytecode manipulation
   * or other mechanisms to improve performance.
   *
   * @param loggingApi the logging API to be identified as the source of log statements (this must
   *        appear somewhere on the stack above the point at which this method is called).
   * @return the log site of the caller of the specified logging API, or {@link LogSite#INVALID} if
   *         the logging API was not found.
   */
  public static LogSite callerOf(Class<?> loggingApi) {
    // Can't skip anything here since someone could pass in LogSite.class.
    return Platform.getCallerFinder().findLogSite(loggingApi, 0);
  }

  /**
   * Returns a {@code LogSite} for the current line of code. This can be used in conjunction with
   * the {@link LoggingApi#withInjectedLogSite(LogSite)} method to implement logging helper
   * methods. In some platforms, log site determination may be unsupported, and in those cases this
   * method will always return the {@link LogSite#INVALID} instance.
   * <p>
   * For example (in {@code MyLoggingHelper}):
   * <pre>{@code
   * public static void logAndSomethingElse(LogSite logSite, String message, Object... args) {
   *   logger.atInfo()
   *       .withInjectedLogSite(logSite)
   *       .logVarargs(message, args);
   * }
   * }</pre>
   * where callers would do:
   * <pre>{@code
   * MyLoggingHelper.logAndSomethingElse(logSite(), "message...");
   * }</pre>
   * <p>
   * Because this method adds an additional parameter and exposes a Flogger specific type to the
   * calling code, you should consider using {@link #callerOf(Class)} for simple logging
   * utilities.
   * <p>
   * It is very important to note that this method can be very slow, since determining the log site
   * can involve stack trace analysis. It is only recommended that it is used for cases where
   * logging is expected to occur (e.g. {@code INFO} level or above). Implementing a helper method
   * for {@code FINE} logging is usually unnecessary (it doesn't normally need to follow any
   * specific "best practice" behavior).
   * <p>
   * Note that even when log site determination is supported, it is not defined as to whether two
   * invocations of this method on the same line of code will produce the same instance, equivalent
   * instances or distinct instance. Thus you should never invoke this method twice in a single
   * statement (and you should never need to).
   * <p>
   * Note that this method call may be replaced in compiled applications via bytecode manipulation
   * or other mechanisms to improve performance.
   *
   * @return the log site of the caller of this method.
   */
  public static LogSite logSite() {
    // Don't call "callerOf()" to avoid making another stack entry.
    return Platform.getCallerFinder().findLogSite(LogSites.class, 0);
  }

  /**
   * Returns a new {@code LogSite} which reflects the information in the given {@link
   * StackTraceElement}, or {@link LogSite#INVALID} if given {@code null}.
   *
   * <p>This method is useful when log site information is only available via an external API which
   * returns {@link StackTraceElement}.
   */
  public static LogSite logSiteFrom(@NullableDecl StackTraceElement e) {
    return e != null ? new StackBasedLogSite(e) : LogSite.INVALID;
  }

  private LogSites() {}
}
