/*
 * Copyright (C) 2014 The Flogger Authors.
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

package com.google.common.flogger.backend.system;

import com.google.common.flogger.backend.LoggerBackend;

/**
 * An API to create logger backends for a given class name. This is implemented as an abstract class
 * (rather than an interface) to reduce to risk of breaking existing implementations if the API
 * changes.
 *
 * <h2>Essential Implementation Restrictions</h2>
 *
 * Any implementation of this API <em>MUST</em> follow the rules listed below to avoid any risk of
 * re-entrant code calling during logger initialization. Failure to do so risks creating complex,
 * hard to debug, issues with Flogger configuration.
 *
 * <ol>
 *   <li>Implementations <em>MUST NOT</em> attempt any logging in static methods or constructors.
 *   <li>Implementations <em>MUST NOT</em> statically depend on any unknown code.
 *   <li>Implementations <em>MUST NOT</em> depend on any unknown code in constructors.
 * </ol>
 *
 * <p>Note that logging and calling arbitrary unknown code (which might log) are permitted inside
 * the instance methods of this API, since they are not called during platform initialization. The
 * easiest way to achieve this is to simply avoid having any non-trivial static fields or any
 * instance fields at all in the implementation.
 *
 * <p>While this sounds onerous it's not difficult to achieve because this API is a singleton, and
 * can delay any actual work until its methods are called. For example if any additional state is
 * required in the implementation, it can be held via a "lazy holder" to defer initialization.
 *
 * <h2>This is a service type</h2>
 *
 * <p>This type is considered a <i>service type</i> and implemenations may be loaded from the
 * classpath via {@link java.util.ServiceLoader} provided the proper service metadata is included in
 * the jar file containing the implementation. When creating an implementation of this class, you
 * can provide serivce metadata (and thereby allow users to get your implementation just by
 * including your jar file) by either manually including a {@code
 * META-INF/services/com.google.common.flogger.backend.system.BackendFactory} file containing the
 * name of your implementation class or by annotating your implementation class using <a
 * href="https://github.com/google/auto/tree/master/service">
 * {@code @AutoService(BackendFactory.class)}</a>. See the documentation of both {@link
 * java.util.ServiceLoader} and {@link DefaultPlatform} for more information.
 */
public abstract class BackendFactory {
  /**
   * Creates a logger backend of the given class name for use by a Fluent Logger. Note that the
   * returned backend need not be unique; one backend could be used by multiple loggers. The given
   * class name must be in the normal dot-separated form (e.g., "com.example.Foo$Bar") rather than
   * the internal binary format "com/example/Foo$Bar").
   *
   * @param loggingClassName the fully-qualified name of the Java class to which the logger is
   *     associated. The logger name is derived from this string in a backend specific way.
   */
  public abstract LoggerBackend create(String loggingClassName);
}
