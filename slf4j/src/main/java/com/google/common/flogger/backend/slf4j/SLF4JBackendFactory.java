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
 * <p>To configure this backend for Flogger set the following system property (also see {@link
 * com.google.common.flogger.backend.system.DefaultPlatform}):
 *
 * <ul>
 * <li>{@code flogger.backend_factory=
 * com.google.common.flogger.backend.slf4j.SLF4JBackendFactory#getInstance}.
 * </ul>
 */
public final class SLF4JBackendFactory extends BackendFactory {

  private static SLF4JBackendFactory INSTANCE = new SLF4JBackendFactory();

  /**
   * This method is expected to be called via reflection (and might otherwise be unused).
   */
  public static BackendFactory getInstance() {
    return INSTANCE;
  }

  @Override
  public LoggerBackend create(String loggingClassName) {
    return new SLF4JLoggerBackend(LoggerFactory.getLogger(loggingClassName.replace('$', '.')));
  }

  @Override
  public String toString() {
    return "SLF4J backend";
  }

  private SLF4JBackendFactory() {
  }
}
