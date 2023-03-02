/*
 * Copyright (C) 2017 The Flogger Authors.
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

import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.backend.LogData;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.LongSubject;
import com.google.common.truth.Subject;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A <a href="https://github.com/google/truth">Truth</a> subject for {@link LogData}. */
public final class LogDataSubject extends Subject {
  private static final Subject.Factory<LogDataSubject, LogData> LOG_DATA_SUBJECT_FACTORY =
      LogDataSubject::new;

  public static Subject.Factory<LogDataSubject, LogData> logData() {
    return LOG_DATA_SUBJECT_FACTORY;
  }

  public static LogDataSubject assertThat(@NullableDecl LogData logData) {
    return assertAbout(logData()).that(logData);
  }

  private final LogData actual;

  private LogDataSubject(FailureMetadata failureMetadata, @NullableDecl LogData subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  /** Asserts about the metadata of this log entry. */
  public MetadataSubject metadata() {
    return check("getMetadata()").about(MetadataSubject.metadata()).that(actual.getMetadata());
  }

  /** Asserts about the nanosecond timestamp of this log entry. */
  public LongSubject timestampNanos() {
    return check("getTimestampNanos()").that(actual.getTimestampNanos());
  }

  /**
   * Asserts that this log entry's message matches the given value. If the log statement for the
   * entry has only a single argument (no formatting), you can write
   * {@code assertLogData(e).hasMessage(value);}.
   */
  public void hasMessage(Object messageOrLiteral) {
    if (actual.getTemplateContext() == null) {
      // Expect literal argument (possibly null).
      check("getLiteralArgument()").that(actual.getLiteralArgument()).isEqualTo(messageOrLiteral);
    } else {
      // Expect message string (non null).
      check("getTemplateContext().getMessage()")
          .that(actual.getTemplateContext().getMessage())
          .isEqualTo(messageOrLiteral);
    }
  }

  /**
   * Asserts that this log entry's arguments match the given values. If the log statement for the
   * entry only a single argument (no formatting), you can write
   * {@code assertLogData(e).hasArguments();}.
   */
  public void hasArguments(Object... args) {
    List<Object> actualArgs = ImmutableList.of();
    if (actual.getTemplateContext() != null) {
      actualArgs = Arrays.asList(actual.getArguments());
    }
    check("getArguments()").that(actualArgs).containsExactly(args).inOrder();
  }

  /** Asserts that this log entry was forced. */
  public void wasForced() {
    if (!actual.wasForced()) {
      failWithActual(simpleFact("expected to be forced"));
    }
  }

  /**
   * Asserts about the log site of the log record.
   */
  public Subject logSite() {
    return check("getLogSite()").that(actual.getLogSite());
  }
}
