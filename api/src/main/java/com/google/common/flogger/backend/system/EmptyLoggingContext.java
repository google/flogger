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

import com.google.common.flogger.backend.Tags;
import java.util.logging.Level;

/** Empty trace context implementation. */
public final class EmptyLoggingContext extends LoggingContext {
  private static final LoggingContext INSTANCE = new EmptyLoggingContext();

  public static LoggingContext getInstance() {
    return INSTANCE;
  }

  private EmptyLoggingContext() {}

  @Override
  public boolean shouldForceLogging(String loggerName, Level level, boolean isEnabled) {
    // Never add any debug or logging here (see LoggingContext for details).
    return false;
  }

  @Override
  public Tags getTags() {
    return Tags.empty();
  }

  @Override
  public String toString() {
    return "Empty logging context";
  }
}
