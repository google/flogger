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

package com.google.common.flogger.testing;

import com.google.common.flogger.LogSite;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** A simplified LogSite implementation used for testing. */
public final class FakeLogSite extends LogSite {
  private static final AtomicInteger uid = new AtomicInteger();

  /** Creates a fake log site (with plausible behavior) from the given parameters. */
  public static LogSite create(
      String className, String methodName, int lineNumber, String sourcePath) {
    return new FakeLogSite(className, methodName, lineNumber, sourcePath);
  }

  /** Creates a unique fake log site for use as a key when testing shared static maps. */
  public static LogSite unique() {
    return create("ClassName", "method_" + uid.incrementAndGet(), 123, "ClassName.java");
  }

  private final String className;
  private final String methodName;
  private final int lineNumber;
  private final String sourcePath;

  private FakeLogSite(String className, String methodName, int lineNumber, String sourcePath) {
    this.className = className;
    this.methodName = methodName;
    this.lineNumber = lineNumber;
    this.sourcePath = sourcePath;
  }

  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public String getMethodName() {
    return methodName;
  }

  @Override
  public int getLineNumber() {
    return lineNumber;
  }

  @Override
  public String getFileName() {
    return sourcePath;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FakeLogSite)) {
      return false;
    }
    FakeLogSite other = (FakeLogSite) obj;
    return Objects.equals(className, other.className)
        && Objects.equals(methodName, other.methodName)
        && lineNumber == other.lineNumber
        && Objects.equals(sourcePath, other.sourcePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(className, methodName, lineNumber, sourcePath);
  }
}
