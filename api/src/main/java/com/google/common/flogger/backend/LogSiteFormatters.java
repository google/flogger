/*
 * Copyright (C) 2023 The Flogger Authors.
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

import com.google.common.flogger.LogSite;

/** Sample LogSiteFormatter implementations. */
public enum LogSiteFormatters implements LogSiteFormatter {
  DEFAULT {
    /** Appends logsite information in the default format, <className>.<methodName>:<lineNumber> */
    @Override
    public boolean appendLogSite(LogSite logSite, StringBuilder out) {
      if (logSite == LogSite.INVALID) {
        return false;
      }
      out.append(logSite.getClassName())
          .append('.')
          .append(logSite.getMethodName())
          .append(':')
          .append(logSite.getLineNumber());
      return true;
    }
  },
  NO_OP {
    /** Does not append logsite information. */
    @Override
    public boolean appendLogSite(LogSite logSite, StringBuilder out) {
      return false;
    }
  },
  SIMPLE_CLASSNAME {
    /**
     * Appends logsite information using the unqualified class name,
     * <simpleClassName>.<methodName>:<lineNumber>
     *
     * <p>The unqualified class name is all text after the last period. A class name with no
     * separators or a trailing separator will be appended in full.
     */
    @Override
    public boolean appendLogSite(LogSite logSite, StringBuilder out) {
      if (logSite == LogSite.INVALID) {
        return false;
      }
      String qualifiedClassName = logSite.getClassName();
      int lastDotIndex = qualifiedClassName.lastIndexOf('.');
      if (lastDotIndex == -1 || lastDotIndex + 1 >= qualifiedClassName.length()) {
        out.append(qualifiedClassName);
      } else {
        out.append(qualifiedClassName, lastDotIndex + 1, qualifiedClassName.length());
      }
      out.append('.').append(logSite.getMethodName()).append(':').append(logSite.getLineNumber());
      return true;
    }
  }
}
