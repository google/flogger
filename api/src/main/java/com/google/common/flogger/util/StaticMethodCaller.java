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

import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Helper to call a no-arg constructor or static getter to obtain an instance of a specified type.
 * This is used for logging platform "plugins". It is expected that these constructors/methods will
 * be invoked once during logger initialization and then the results cached in the platform class
 * (thus there is no requirement for the class being invoked to handle caching of the result).
 */
public final class StaticMethodCaller {
  // TODO(cgdecker): Rename this class; eventually perhaps just roll it into DefaultPlatform

  /**
   * Attempts to get an instance of the given {@code type} that is specified by the given {@code
   * propertyName}, returning {@code null} if that is not possible for any reason.
   *
   * <p>The property's value, if present, is expected to be one of:
   *
   * <ol>
   *   <li>A fully-qualified class name, in which case the instance is obtained by invoking the
   *       class's no-arg static {@code getInstance} method, if present, or public no-arg
   *       constructor.
   *   <li>A fully-qualified class name followed by {@code #} and a method name, in which case the
   *       instance is obtained by invoking a no-arg static method of that name, if present, or a
   *       public no-arg constructor if not present.
   * </ol>
   */
  public static <T> T getInstanceFromSystemProperty(String propertyName, Class<T> type) {
    return getInstanceFromSystemProperty(propertyName, null, type);
  }

  /**
   * Attempts to get an instance of the given {@code type} that is specified by the given {@code
   * propertyName}, returning {@code null} if that is not possible for any reason.
   *
   * <p>The property's value, if present, or the given {@code defaultValue}, is expected to be one
   * of:
   *
   * <ol>
   *   <li>A fully-qualified class name, in which case the instance is obtained by invoking the
   *       class's no-arg static {@code getInstance} method, if present, or public no-arg
   *       constructor.
   *   <li>A fully-qualified class name followed by {@code #} and a method name, in which case the
   *       instance is obtained by invoking a no-arg static method of that name, if present, or a
   *       public no-arg constructor if not present.
   * </ol>
   */
  @NullableDecl
  public static <T> T getInstanceFromSystemProperty(
      String propertyName, @NullableDecl String defaultValue, Class<T> type) {
    String property = readProperty(propertyName, defaultValue);
    if (property == null) {
      return null;
    }

    int hashIndex = property.indexOf('#');
    String className = hashIndex == -1 ? property : property.substring(0, hashIndex);
    // TODO(cgdecker): Eventually we should eleminate method checks and only use constructors
    String methodName = hashIndex == -1 ? "getInstance" : property.substring(hashIndex + 1);

    String attemptedMethod = className + '#' + methodName + "()";
    try {
      Class<?> clazz = Class.forName(className);
      try {
        Method method = clazz.getMethod(methodName);
        // If the method exists, try to invoke it and don't fall back to the constructor if it
        // fails. The fallback is only for the case where the method in question has been removed.
        return type.cast(method.invoke(null));
      } catch (NoSuchMethodException e) {
          // If the user explicitly specified a getInstance method via "ClassName#getInstance" and
          // that getInstance method doesn't exist, fall back to constructor invocation. This allows
          // system properties that were set for service types Flogger provides to continue to work
          // even though we intentionally removed their getInstance() methods.
        if (hashIndex == -1 || !methodName.equals("getInstance")) {
          // Otherwise, error and return
          error("method '%s' does not exist: %s\n", property, e);
          return null;
        }
      }

      // The method didn't exist, try the constructor
      attemptedMethod = "new " + className + "()";
      return type.cast(clazz.getConstructor().newInstance());
    } catch (ClassNotFoundException e) {
      // Expected if an optional aspect is not being used (no error).
    } catch (ClassCastException e) {
      error("cannot cast result of calling '%s' to '%s': %s\n", attemptedMethod, type.getName(), e);
    } catch (Exception e) {
      // Catches SecurityException *and* ReflexiveOperationException (which doesn't exist in 1.6).
      error(
          "cannot call expected no-argument constructor or static method '%s': %s\n",
          attemptedMethod, e);
    }
    return null;
  }

  private static String readProperty(String propertyName, @NullableDecl String defaultValue) {
    Checks.checkNotNull(propertyName, "property name");
    try {
      return System.getProperty(propertyName, defaultValue);
    } catch (SecurityException e) {
      error("cannot read property name %s: %s", propertyName, e);
    }
    return null;
  }

  // This cannot use a fluent logger here and it's even risky to use a JDK logger.
  private static void error(String msg, Object... args) {
    System.err.println(StaticMethodCaller.class + ": " + String.format(msg, args));
  }

  private StaticMethodCaller() {}
}
