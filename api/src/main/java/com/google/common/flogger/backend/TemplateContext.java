/*
 * Copyright (C) 2014 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.common.flogger.parser.MessageParser;

/**
 * A context object for templates that allows caches to validate existing templates or create new
 * ones. If two template contexts are equal (via {@link #equals}) then the templates they produce
 * are interchangeable.
 * <p>
 * Template contexts are created by the frontend and passed through to backend implementations via
 * the {@link LogData} interface.
 */
public final class TemplateContext {
  private final MessageParser parser;
  private final String message;

  /** Creates a template context for a log statement. */
  public TemplateContext(MessageParser parser, String message) {
    this.parser = checkNotNull(parser, "parser");
    this.message = checkNotNull(message, "message");
  }

  /** Returns the message parser for the log statement. */
  public MessageParser getParser() {
    return parser;
  }

  /** Returns the message for the log statement. */
  public String getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TemplateContext) {
      TemplateContext other = (TemplateContext) obj;
      return parser.equals(other.parser) && message.equals(other.message);
    }
    return false;
  }

  @Override
  public int hashCode() {
    // We don't expect people to be using the context as a cache key, but it should work.
    return parser.hashCode() ^ message.hashCode();
  }
}
