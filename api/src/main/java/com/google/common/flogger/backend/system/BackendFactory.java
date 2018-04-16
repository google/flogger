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
 * An API to create logger backends for a given class name. This is implemented as an abstract
 * class (rather than an interface) to reduce to risk of breaking existing implementations if the
 * API changes.
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
