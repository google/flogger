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

package com.google.common.flogger.backend.log4j;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.system.BackendFactory;
import org.apache.log4j.Logger;

/**
 * BackendFactory for log4j.
 *
 * <p>When using Flogger's {@link com.google.common.flogger.backend.system.DefaultPlatform}, this
 * factory will automatically be used if it is included on the classpath and no other implementation
 * of {@code BackendFactory} (other than the default implementation) is. To specify it more
 * explicitly or to work around an issue where multiple {@code BackendFactory} implementations are
 * on the classpath, you can set the {@code flogger.backend_factory} system property:
 *
 * <ul>
 *   <li>{@code flogger.backend_factory=com.google.common.flogger.backend.log4j.Log4jBackendFactory}
 * </ul>
 *
 * <p>Note: This code is mostly derived from the equivalently named class in the Log4j2 backend
 * implementation, and should be kept in-sync with it as far as possible. If possible, any changes
 * to the functionality of this class should first be made in the log4j2 backend and then reflected
 * here. If the behaviour of this class starts to deviate from that of the log4j2 backend in any
 * significant way, this difference should be called out clearly in the documentation.
 */
public final class Log4jBackendFactory extends BackendFactory {

  // Must be public for ServiceLoader
  public Log4jBackendFactory() {}

  @Override
  public LoggerBackend create(String loggingClassName) {
    // Compute the logger name exactly the same way as in SimpleBackendFactory.
    // The logger name must match the name of the logging class so that we can return it from
    // Log4jLoggerBackend#getName().
    // TODO(b/27920233): Strip inner/nested classes when deriving logger name.
    Logger logger = Logger.getLogger(loggingClassName.replace('$', '.'));
    return new Log4jLoggerBackend(logger);
  }

  @Override
  public String toString() {
    return "Log4j backend";
  }
}
