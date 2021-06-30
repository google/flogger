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

package com.google.common.flogger.backend.log4j2;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.system.BackendFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

/**
 * BackendFactory for log4j2.
 *
 * <p>When using Flogger's {@link com.google.common.flogger.backend.system.DefaultPlatform}, this
 * factory will automatically be used if it is included on the classpath and no other implementation
 * of {@code BackendFactory} (other than the default implementation) is. To specify it more
 * explicitly or to work around an issue where multiple {@code BackendFactory} implementations are
 * on the classpath, you can set the {@code flogger.backend_factory} system property:
 *
 * <ul>
 *   <li>{@code
 *       flogger.backend_factory=com.google.common.flogger.backend.log4j2.Log4j2BackendFactory}
 * </ul>
 *
 * <p>Note: Any changes in this code should, as far as possible, be reflected in the equivalently
 * named log4j implementation. If the behaviour of this class starts to deviate from that of the
 * log4j backend in any significant way, this difference should be called out clearly in the
 * documentation.
 */
public final class Log4j2BackendFactory extends BackendFactory {

  // Must be public for ServiceLoader
  public Log4j2BackendFactory() {}

  @Override
  public LoggerBackend create(String loggingClassName) {
    // Compute the logger name exactly the same way as in SimpleBackendFactory.
    // The logger name must match the name of the logging class so that we can return it from
    // Log4j2LoggerBackend#getLoggerName().
    // We cast org.apache.logging.log4j.core.Logger here so that
    // we can access the methods only avilable under org.apache.logging.log4j.core.Logger.
    // TODO(b/27920233): Strip inner/nested classes when deriving logger name.
    Logger logger = (Logger) LogManager.getLogger(loggingClassName.replace('$', '.'));
    return new Log4j2LoggerBackend(logger);
  }

  @Override
  public String toString() {
    return "Log4j2 backend";
  }
}
