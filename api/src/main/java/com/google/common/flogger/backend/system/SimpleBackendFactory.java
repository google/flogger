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

package com.google.common.flogger.backend.system;

import com.google.common.flogger.backend.LoggerBackend;
import java.util.logging.Logger;

/**
 * Default factory for creating logger backends.
 *
 * <p>See class documentation in {@link BackendFactory} for important implementation restrictions.
 */
public final class SimpleBackendFactory extends BackendFactory {
  private static final BackendFactory INSTANCE = new SimpleBackendFactory();

  // Called during logging platform initialization; MUST NOT call any code that might log.
  public static BackendFactory getInstance() {
    return INSTANCE;
  }

  private SimpleBackendFactory() {}

  @Override
  public LoggerBackend create(String loggingClass) {
    // TODO(b/27920233): Strip inner/nested classes when deriving logger name.
    Logger logger = Logger.getLogger(loggingClass.replace('$', '.'));
    return new SimpleLoggerBackend(logger);
  }

  @Override
  public String toString() {
    // This should probably be changed (it's not useful if it doesn't contain the class name).
    return "Default logger backend factory";
  }
}
