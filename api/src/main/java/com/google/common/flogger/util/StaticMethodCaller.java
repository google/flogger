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

package com.google.common.flogger.util;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Helper to call a static no-arg getter to obtain an instance of a specified type. This is used
 * for logging platform "plugins" which are expected to have a singleton available. It is expected
 * that these getter methods will be invoked once during logger initialization and then the results
 * cached in the platform class (thus there is no requirement for the class being invoked to handle
 * efficient caching of the result).
 */
public final class StaticMethodCaller {
  /**
   * Returns the value of the specified static no-argument method, or null if the method cannot be
   * called or the returned value is of the wrong type.
   *
   * @param propertyName the name of a system property which is expected to hold a value like
   *     {@code "com.foo.Bar#someMethod"}, where the referenced method is a public, no-argument
   *     getter for an instance of the given type.
   * @param type the expected type (or supertype) of the returned value (generified types are not
   *     supported).
   */
  @Nullable
  public static <T> T callGetterFromSystemProperty(String propertyName, Class<T> type) {
    String getter = readProperty(propertyName);
    if (getter == null) {
      return null;
    }
    int idx = getter.indexOf('#');
    if (idx <= 0 || idx == getter.length() - 1) {
      error("invalid getter (expected <class>#<method>): %s\n", getter);
      return null;
    }
    return callStaticMethod(getter.substring(0, idx), getter.substring(idx + 1), type);
  }

  private static String readProperty(String propertyName) {
    Checks.checkNotNull(propertyName, "property name");
    try {
      return System.getProperty(propertyName);
    } catch (SecurityException e) {
      error("cannot read property name %s: %s", propertyName, e);
    }
    return null;
  }

  private static <T> T callStaticMethod(String className, String methodName, Class<T> type) {
    try {
      return type.cast(Class.forName(className).getMethod(methodName).invoke(null));
    } catch (ClassNotFoundException e) {
      // Expected if an optional aspect is not being used (no error).
    } catch (ClassCastException e) {
      error("cannot cast result of calling '%s#%s' to '%s': %s\n",
          className, methodName, type.getName(), e);
    } catch (Exception e) {
      // Catches SecurityException *and* ReflexiveOperationException (which doesn't exist in 1.6).
      error("cannot call expected no-argument static method '%s#%s': %s\n",
          className, methodName, e);
    }
    return null;
  }

  // This cannot use a fluent logger here and it's even risky to use a JDK logger.
  private static void error(String msg, Object... args) {
    System.err.println(StaticMethodCaller.class + ": " + String.format(msg, args));
  }

  private StaticMethodCaller() {}
}
