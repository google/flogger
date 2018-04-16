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

package com.google.common.flogger.backend.system;

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.backend.TemplateContext;

/**
 * A template for a single log statement including all user specified formatting information.
 * Template instances must be thread-safe and immutable.
 * <p>
 * This base class simply associates a context with each template, which can be used by caches to
 * validate existing templates or create new ones.
 */
public abstract class AbstractMessageTemplate {
  private final TemplateContext context;

  public AbstractMessageTemplate(TemplateContext context) {
    this.context = checkNotNull(context, "context");
  }

  /**
   * Returns the context for this template. If two contexts are equal then the templates they
   * produce are interchangeable.
   */
  public TemplateContext getContext() {
    return context;
  }
}
