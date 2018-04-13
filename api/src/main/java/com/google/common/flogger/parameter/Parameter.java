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

package com.google.common.flogger.parameter;

import com.google.common.flogger.backend.FormatOptions;

/**
 * An abstract representation of a parameter for a message template.
 * <p>
 * Note that this is implemented as a class (rather than via an interface) because it is very
 * helpful to have explicit checks for the index values and count to ensure we can calculate
 * reliable low bounds for the number of arguments a template can accept.
 * <p>
 * Note that all subclasses of Parameter must be immutable and thread safe.
 */
public abstract class Parameter {
  private final int index;
  private final FormatOptions options;

  /**
   * Constructs a parameter to format an argument using specified formatting options.
   *
   * @param options the format options for this parameter.
   * @param index the index of the argument processed by this parameter.
   */
  protected Parameter(FormatOptions options, int index) {
    if (options == null) {
      throw new IllegalArgumentException("format options cannot be null");
    }
    if (index < 0) {
      throw new IllegalArgumentException("invalid index: " + index);
    }
    this.index = index;
    this.options = options;
  }

  /** Returns the index of the argument to be processed by this parameter. */
  public final int getIndex() {
    return index;
  }

  /** Returns the formatting options. */
  protected final FormatOptions getFormatOptions() {
    return options;
  }

  public final void accept(ParameterVisitor visitor, Object[] args) {
    if (getIndex() < args.length) {
      Object value = args[getIndex()];
      if (value != null) {
        accept(visitor, value);
      } else {
        visitor.visitNull();
      }
    } else {
      visitor.visitMissing();
    }
  }

  protected abstract void accept(ParameterVisitor visitor, Object value);

  /**
   * Returns the printf format string specified for this parameter (eg, "%d" or "%tc").
   */
  public abstract String getFormat();
}
