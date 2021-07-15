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

package com.google.common.flogger.backend.slf4j;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.system.BackendFactory;
import org.slf4j.LoggerFactory;

/**
 * BackendFactory for SLF4J.
 *
 * <p>When using Flogger's {@link com.google.common.flogger.backend.system.DefaultPlatform}, this
 * factory will automatically be used if it is included on the classpath and no other implementation
 * of {@code BackendFactory} (other than the default implementation) is. To specify it more
 * explicitly or to work around an issue where multiple {@code BackendFactory} implementations are
 * on the classpath, you can set the {@code flogger.backend_factory} system property:
 *
 * <ul>
 *   <li>{@code flogger.backend_factory=com.google.common.flogger.backend.slf4j.Slf4jBackendFactory}
 * </ul>
 */
public final class Slf4jBackendFactory extends BackendFactory {

  // Must be public for ServiceLoader
  public Slf4jBackendFactory() {}

  @Override
  public LoggerBackend create(String loggingClassName) {
    return new Slf4jLoggerBackend(LoggerFactory.getLogger(loggingClassName.replace('$', '.')));
  }

  @Override
  public String toString() {
    return "SLF4J backend";
  }
}
