/*
 * Copyright (C) 2015 The Flogger Authors.
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

package com.google.common.flogger.testing;

import static com.google.common.flogger.util.Checks.checkState;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.TemplateContext;
import com.google.common.flogger.parser.DefaultBraceStyleMessageParser;
import com.google.common.flogger.parser.DefaultPrintfMessageParser;
import com.google.common.flogger.parser.MessageParser;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.logging.Level;

/**
 * A mutable fake {@link LogData} implementation to help test logging backends and other log
 * handling code.
 */
public final class FakeLogData implements LogData {
  public static final String FAKE_LOGGER_NAME = "com.google.LoggerName";

  public static final String FAKE_LOGGING_CLASS = "com.google.FakeClass";
  public static final String FAKE_LOGGING_METHOD = "fakeMethod";
  public static final String FAKE_SOURCE_PATH = "src/com/google/FakeClass.java";

  public static final LogSite FAKE_LOG_SITE =
      FakeLogSite.create(FAKE_LOGGING_CLASS, FAKE_LOGGING_METHOD, 123, FAKE_SOURCE_PATH);

  /**
   * Creates a fake {@code LogData} instance representing a log statement with a single, literal
   * argument.
   */
  public static FakeLogData of(Object literalArgument) {
    return new FakeLogData(literalArgument);
  }

  /** Creates a fake {@code LogData} instance for a log statement with printf style formatting. */
  public static FakeLogData withPrintfStyle(String message, Object... arguments) {
    return new FakeLogData(DefaultPrintfMessageParser.getInstance(), message, arguments);
  }

  /** Creates a fake {@code LogData} instance for a log statement with brace style formatting. */
  public static FakeLogData withBraceStyle(String message, Object... arguments) {
    return new FakeLogData(DefaultBraceStyleMessageParser.getInstance(), message, arguments);
  }

  private Level level = Level.INFO;
  private TemplateContext context = null;
  private Object[] arguments = null;
  private Object literalArgument = null;
  private long timestampNanos = 0L;
  private FakeMetadata metadata = new FakeMetadata();
  private LogSite logSite = FAKE_LOG_SITE;

  private FakeLogData(Object literalArgument) {
    this.literalArgument = literalArgument;
  }

  private FakeLogData(MessageParser parser, String message, Object... arguments) {
    this.context = new TemplateContext(parser, message);
    this.arguments = arguments;
  }

  @SuppressWarnings("GoodTime") // should accept a java.time.Instant
  @CanIgnoreReturnValue
  public FakeLogData setTimestampNanos(long timestampNanos) {
    this.timestampNanos = timestampNanos;
    return this;
  }

  @CanIgnoreReturnValue
  public FakeLogData setLevel(Level level) {
    this.level = level;
    return this;
  }

  @CanIgnoreReturnValue
  public FakeLogData setLogSite(LogSite logSite) {
    this.logSite = logSite;
    return this;
  }

  @CanIgnoreReturnValue
  public <T> FakeLogData addMetadata(MetadataKey<T> key, Object value) {
    metadata.add(key, key.cast(value));
    return this;
  }

  @Override
  public Level getLevel() {
    return level;
  }

  @Deprecated
  @Override
  public long getTimestampMicros() {
    return NANOSECONDS.toMicros(timestampNanos);
  }

  @Override
  public long getTimestampNanos() {
    return timestampNanos;
  }

  @Override
  public String getLoggerName() {
    return FAKE_LOGGER_NAME;
  }

  @Override
  public LogSite getLogSite() {
    return logSite;
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  @Override
  public boolean wasForced() {
    // Check explicit TRUE here because findValue() can return null (which would fail unboxing).
    return Boolean.TRUE.equals(metadata.findValue(LogContext.Key.WAS_FORCED));
  }

  @Override
  public TemplateContext getTemplateContext() {
    return context;
  }

  @Override
  public Object[] getArguments() {
    checkState(context != null, "cannot get arguments without a context");
    return arguments;
  }

  @Override
  public Object getLiteralArgument() {
    checkState(context == null, "cannot get literal argument if context exists");
    return literalArgument;
  }
}
