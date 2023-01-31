/*
 * Copyright (C) 2015 The Flogger Authors.
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
import static java.lang.Math.max;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A stack based log site which uses information from a given {@code StackTraceElement}.
 *
 * <p>Unlike truly unique injected log sites, StackBasedLogSite falls back to using the class name,
 * method name and line number for {@code equals()} and {@code hashcode()}. This makes it almost as
 * good as a globally unique instance in most cases, except if either of the following is true:
 *
 * <ul>
 *   <li>There are two log statements on a single line.
 *   <li>Line number information is stripped from the class.
 * </ul>
 *
 * <p>This class should not be used directly outside the core Flogger libraries. If you need to
 * generate a {@link LogSite} from a {@link StackTraceElement}, use {@link
 * com.google.common.flogger.LogSites#logSiteFrom(StackTraceElement)
 * LogSites.logSiteFrom(myStackTaceElement)}.
 */
final class StackBasedLogSite extends LogSite {
  // StackTraceElement is unmodifiable once created.
  private final StackTraceElement stackElement;

  public StackBasedLogSite(StackTraceElement stackElement) {
    this.stackElement = checkNotNull(stackElement, "stack element");
  }

  @Override
  public String getClassName() {
    return stackElement.getClassName();
  }

  @Override
  public String getMethodName() {
    return stackElement.getMethodName();
  }

  @Override
  public int getLineNumber() {
    // Prohibit negative numbers (which can appear in stack trace elements) from being returned.
    return max(stackElement.getLineNumber(), LogSite.UNKNOWN_LINE);
  }

  @Override
  public String getFileName() {
    return stackElement.getFileName();
  }

  @Override
  public boolean equals(@NullableDecl Object obj) {
    return (obj instanceof StackBasedLogSite)
        && stackElement.equals(((StackBasedLogSite) obj).stackElement);
  }

  @Override
  public int hashCode() {
    // Note that (unlike other log site implementations) this hash-code appears to include the
    // file name when creating a hashcode, but this should be the same every time a stack trace
    // element is created, so it shouldn't be a problem.
    return stackElement.hashCode();
  }
}
