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

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.backend.LogData;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A <a href="https://github.com/google/truth">Truth</a> subject for {@link LogData}.
 */
@CheckReturnValue
public final class LogDataSubject extends Subject<LogDataSubject, LogData> {
  private static final Subject.Factory<LogDataSubject, LogData> LOG_DATA_SUBJECT_FACTORY =
      LogDataSubject::new;

  public static Subject.Factory<LogDataSubject, LogData> logData() {
    return LOG_DATA_SUBJECT_FACTORY;
  }

  public static LogDataSubject assertThat(@Nullable LogData logData) {
    return assertAbout(logData()).that(logData);
  }

  private LogDataSubject(FailureMetadata failureMetadata, @Nullable LogData subject) {
    super(failureMetadata, subject);
  }

  /** Asserts about the metadata of this log entry. */
  public MetadataSubject metadata() {
    return check("getMetadata()").about(MetadataSubject.metadata()).that(actual().getMetadata());
  }

  /**
   * Asserts that this log entry's message matches the given value. If the log statement for the
   * entry has only a single argument (no formatting), you can write
   * {@code assertLogData(e).hasMessage(value);}.
   */
  public void hasMessage(Object messageOrLiteral) {
    if (actual().getTemplateContext() == null) {
      // Expect literal argument (possibly null).
      check("getLiteralArgument()").that(actual().getLiteralArgument()).isEqualTo(messageOrLiteral);
    } else {
      // Expect message string (non null).
      check("getTemplateContext().getMessage()")
          .that(actual().getTemplateContext().getMessage())
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
    if (actual().getTemplateContext() != null) {
      actualArgs = Arrays.asList(actual().getArguments());
    }
    check("getArguments()").that(actualArgs).containsExactly(args).inOrder();
  }

  /** Asserts that this log entry was forced. */
  public void wasForced() {
    if (!actual().wasForced()) {
      fail("was forced");
    }
  }
}
