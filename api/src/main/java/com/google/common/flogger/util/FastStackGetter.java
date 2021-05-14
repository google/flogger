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

package com.google.common.flogger.util;

import com.google.errorprone.annotations.CheckReturnValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A helper class to abstract the complexities of dynamically invoking the
 * {@code getStackTraceElement()} method of {@code JavaLangAccess}.
 */
@CheckReturnValue
final class FastStackGetter {
  /**
   * @return a new {@code FastStackGetter} if the {@code getStackTraceElement()} method of
   * {@code JavaLangAccess} is supported on this platform, or {@code null} otherwise.
   */
  @NullableDecl
  public static FastStackGetter createIfSupported() {
    try {
      Object javaLangAccess =
          Class.forName("sun.misc.SharedSecrets").getMethod("getJavaLangAccess").invoke(null);
      // NOTE: We do not use "javaLangAccess.getClass()" here because that's the implementation,
      // not the interface and we must obtain the reflected Method from the interface directly.
      Method getElementMethod =
          Class.forName("sun.misc.JavaLangAccess")
              .getMethod("getStackTraceElement", Throwable.class, int.class);
      Method getDepthMethod =
          Class.forName("sun.misc.JavaLangAccess").getMethod("getStackTraceDepth", Throwable.class);

      // To really be sure that we can use these later without issue, just call them now (including
      // the cast of the returned value).
      @SuppressWarnings("unused")
      StackTraceElement unusedElement =
          (StackTraceElement) getElementMethod.invoke(javaLangAccess, new Throwable(), 0);
      @SuppressWarnings("unused")
      int unusedDepth = (int) (Integer) getDepthMethod.invoke(javaLangAccess, new Throwable());

      return new FastStackGetter(javaLangAccess, getElementMethod, getDepthMethod);
    } catch (ThreadDeath t) {
      // Do not stop ThreadDeath from propagating.
      throw t;
    } catch (Throwable t) {
      // If creation fails for any reason we return null, which results in the logger agent falling
      // back to the (much) slower getStackTrace() method.
      return null;
    }
  }

  /** The implementation of {@code sun.misc.JavaLangAccess} for this platform (if it exists). */
  private final Object javaLangAccess;
  /** The {@code getStackTraceElement(Throwable, int)} method of {@code sun.misc.JavaLangAccess}. */
  private final Method getElementMethod;
  /** The {@code getStackTraceDepth(Throwable)} method of {@code sun.misc.JavaLangAccess}. */
  private final Method getDepthMethod;

  private FastStackGetter(Object javaLangAccess, Method getElementMethod, Method getDepthMethod) {
    this.javaLangAccess = javaLangAccess;
    this.getElementMethod = getElementMethod;
    this.getDepthMethod = getDepthMethod;
  }

  /**
   * Mimics a direct call to {@code getStackTraceElement()} on the JavaLangAccess interface without
   * requiring the Java runtime to directly reference the method (which may not exist on some
   * JVMs).
   */
  public StackTraceElement getStackTraceElement(Throwable throwable, int n) {
    try {
      return (StackTraceElement) getElementMethod.invoke(javaLangAccess, throwable, n);
    } catch (InvocationTargetException e) {
      // The only case we should expect to see here normally is a wrapped IndexOutOfBoundsException.
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      } else if (e.getCause() instanceof Error) {
        throw (Error) e.getCause();
      }
      // This should not be possible because the getStackTraceElement() method does not declare
      // any checked exceptions (though APIs may change over time).
      throw new RuntimeException(e.getCause());
    } catch (IllegalAccessException e) {
      // This should never happen because the method has been successfully invoked once already.
      throw new AssertionError(e);
    }
  }

  /**
   * Mimics a direct call to {@code getStackTraceDepth()} on the JavaLangAccess interface without
   * requiring the Java runtime to directly reference the method (which may not exist on some
   * JVMs).
   */
  public int getStackTraceDepth(Throwable throwable) {
    try {
      return (int) (Integer) getDepthMethod.invoke(javaLangAccess, throwable);
    } catch (InvocationTargetException e) {
      // There really should be no chance of runtime errors.
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      } else if (e.getCause() instanceof Error) {
        throw (Error) e.getCause();
      }
      // This should not be possible because the getStackTraceElement() method does not declare
      // any checked exceptions (though APIs may change over time).
      throw new RuntimeException(e.getCause());
    } catch (IllegalAccessException e) {
      // This should never happen because the method has been successfully invoked once already.
      throw new AssertionError(e);
    }
  }
}
