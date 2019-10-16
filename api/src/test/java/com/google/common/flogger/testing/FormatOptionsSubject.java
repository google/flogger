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

import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.flogger.backend.FormatChar;
import com.google.common.flogger.backend.FormatOptions;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * A <a href="https://github.com/google/truth">Truth</a> subject for {@link FormatOptions}.
 *
 * @author Kurt Alfred Kluever (kak@google.com)
 */
public final class FormatOptionsSubject extends Subject {

  public static FormatOptionsSubject assertThat(@NullableDecl FormatOptions formatOptions) {
    return assertAbout(FormatOptionsSubject.FORMAT_OPTIONS_FACTORY).that(formatOptions);
  }

  private static final Subject.Factory<FormatOptionsSubject, FormatOptions> FORMAT_OPTIONS_FACTORY =
      FormatOptionsSubject::new;

  private final FormatOptions actual;

  private FormatOptionsSubject(FailureMetadata failureMetadata, @NullableDecl FormatOptions subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  public void isDefault() {
    if (!actual.isDefault()) {
      failWithActual(simpleFact("expected to be default"));
    }
  }

  public void hasPrecision(int precision) {
    check("getPrecision()").that(actual.getPrecision()).isEqualTo(precision);
  }

  public void hasWidth(int width) {
    check("getWidth()").that(actual.getWidth()).isEqualTo(width);
  }

  public void hasNoFlags() {
    if (actual.getFlags() != 0) {
      failWithActual(simpleFact("expected to have no flags"));
    }
  }

  public void shouldUpperCase() {
    if (!actual.shouldUpperCase()) {
      failWithActual(simpleFact("expected to upper case"));
    }
  }

  public void shouldntUpperCase() {
    if (actual.shouldUpperCase()) {
      failWithActual(simpleFact("expected not to upper case"));
    }
  }

  public void shouldLeftAlign() {
    if (!actual.shouldLeftAlign()) {
      failWithActual(simpleFact("expected to left align"));
    }
  }

  public void shouldntLeftAlign() {
    if (actual.shouldLeftAlign()) {
      failWithActual(simpleFact("expected not to left align"));
    }
  }

  public void shouldShowAltForm() {
    if (!actual.shouldShowAltForm()) {
      failWithActual(simpleFact("expected to show alt form"));
    }
  }

  public void shouldntShowAltForm() {
    if (actual.shouldShowAltForm()) {
      failWithActual(simpleFact("expected not to show alt form"));
    }
  }

  public void shouldShowGrouping() {
    if (!actual.shouldShowGrouping()) {
      failWithActual(simpleFact("expected to show grouping"));
    }
  }

  public void shouldntShowGrouping() {
    if (actual.shouldShowGrouping()) {
      failWithActual(simpleFact("expected not to show grouping"));
    }
  }

  public void shouldShowLeadingZeros() {
    if (!actual.shouldShowLeadingZeros()) {
      failWithActual(simpleFact("expected to show leading zeros"));
    }
  }

  public void shouldntShowLeadingZeros() {
    if (actual.shouldShowLeadingZeros()) {
      failWithActual(simpleFact("expected not to show leading zeros"));
    }
  }

  public void shouldPrefixSpaceForPositiveValues() {
    if (!actual.shouldPrefixSpaceForPositiveValues()) {
      failWithActual(simpleFact("expected to prefix space for positive values"));
    }
  }

  public void shouldntPrefixSpaceForPositiveValues() {
    if (actual.shouldPrefixSpaceForPositiveValues()) {
      failWithActual(simpleFact("expected not to prefix space for positive values"));
    }
  }

  public void shouldPrefixPlusForPositiveValues() {
    if (!actual.shouldPrefixPlusForPositiveValues()) {
      failWithActual(simpleFact("expected to prefix plus for positive values"));
    }
  }

  public void shouldntPrefixPlusForPositiveValues() {
    if (actual.shouldPrefixPlusForPositiveValues()) {
      failWithActual(simpleFact("expected not to prefix plus for positive values"));
    }
  }

  public void areValidFor(FormatChar formatChar) {
    if (!actual.areValidFor(formatChar)) {
      failWithActual("expected to be valid for", formatChar);
    }
  }

  public void areNotValidFor(FormatChar formatChar) {
    if (actual.areValidFor(formatChar)) {
      failWithActual("expected not to be valid for", formatChar);
    }
  }
}
