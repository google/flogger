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

package com.google.common.flogger.backend;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Exception thrown when a log statement cannot be emitted correctly. This exception should only be
 * thrown by logger backend implementations which have opted not to handle specific issues.
 * <p>
 * Typically a logger backend would only throw {@code LoggingException} in response to issues in
 * test code or other debugging environments. In production code, the backend should be configured
 * to emit a modified log statement which includes the error information.
 * <p>
 * See also {@link LoggerBackend#handleError(RuntimeException, LogData)}.
 */
public class LoggingException extends RuntimeException {

  public LoggingException(@Nullable String message) {
    super(message);
  }

  public LoggingException(@Nullable String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
