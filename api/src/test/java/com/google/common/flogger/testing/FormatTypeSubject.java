/*
 * Copyright (C) 2016 The Flogger Authors.
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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.flogger.backend.FormatType;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A <a href="https://github.com/google/truth">Truth</a> subject for {@link FormatType}.
 *
 * @author Kurt Alfred Kluever (kak@google.com)
 */
public final class FormatTypeSubject extends Subject {

  public static FormatTypeSubject assertThat(@NullableDecl FormatType formatType) {
    return assertAbout(FormatTypeSubject.FORMAT_TYPE_SUBJECT_FACTORY).that(formatType);
  }

  private static final Subject.Factory<FormatTypeSubject, FormatType> FORMAT_TYPE_SUBJECT_FACTORY =
      FormatTypeSubject::new;

  private final FormatType actual;

  private FormatTypeSubject(FailureMetadata failureMetadata, @NullableDecl FormatType subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  public void canFormat(Object arg) {
    assertWithMessage("Unable to format " + arg + " using " + actual)
        .that(actual.canFormat(arg))
        .isTrue();
  }

  public void cannotFormat(Object arg) {
    assertWithMessage("Expected error when formatting " + arg + " using " + actual)
        .that(actual.canFormat(arg))
        .isFalse();
  }

  public void isNumeric() {
    check("isNumeric()")
        .withMessage("Expected " + actual + " to be numeric but wasn't")
        .that(actual.isNumeric())
        .isTrue();
  }

  public void isNotNumeric() {
    check("isNumeric()")
        .withMessage("Expected " + actual + " to not be numeric but was")
        .that(actual.isNumeric())
        .isFalse();
  }
}
