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
import javax.annotation.Nullable;

/**
 * A <a href="https://github.com/google/truth">Truth</a> subject for {@link FormatOptions}.
 *
 * @author Kurt Alfred Kluever (kak@google.com)
 */
public final class FormatOptionsSubject extends Subject<FormatOptionsSubject, FormatOptions> {

  public static FormatOptionsSubject assertThat(@Nullable FormatOptions formatOptions) {
    return assertAbout(FormatOptionsSubject.FORMAT_OPTIONS_FACTORY).that(formatOptions);
  }

  private static final Subject.Factory<FormatOptionsSubject, FormatOptions> FORMAT_OPTIONS_FACTORY =
      FormatOptionsSubject::new;

  private FormatOptionsSubject(FailureMetadata failureMetadata, @Nullable FormatOptions subject) {
    super(failureMetadata, subject);
  }

  public void isDefault() {
    if (!actual().isDefault()) {
      failWithActual(simpleFact("expected to be default"));
    }
  }

  public void hasPrecision(int precision) {
    check("getPrecision()").that(actual().getPrecision()).isEqualTo(precision);
  }

  public void hasWidth(int width) {
    check("getWidth()").that(actual().getWidth()).isEqualTo(width);
  }

  public void hasNoFlags() {
    if (actual().getFlags() != 0) {
      failWithActual(simpleFact("expected to have no flags"));
    }
  }

  public void shouldUpperCase() {
    if (!actual().shouldUpperCase()) {
      fail("should upper case");
    }
  }

  public void shouldntUpperCase() {
    if (actual().shouldUpperCase()) {
      fail("shouldn't upper case");
    }
  }

  public void shouldLeftAlign() {
    if (!actual().shouldLeftAlign()) {
      fail("should left align");
    }
  }

  public void shouldntLeftAlign() {
    if (actual().shouldLeftAlign()) {
      fail("shouldn't left align");
    }
  }

  public void shouldShowAltForm() {
    if (!actual().shouldShowAltForm()) {
      fail("should show alt form");
    }
  }

  public void shouldntShowAltForm() {
    if (actual().shouldShowAltForm()) {
      fail("shouldn't show alt form");
    }
  }

  public void shouldShowGrouping() {
    if (!actual().shouldShowGrouping()) {
      fail("should show grouping");
    }
  }

  public void shouldntShowGrouping() {
    if (actual().shouldShowGrouping()) {
      fail("shouldn't show grouping");
    }
  }

  public void shouldShowLeadingZeros() {
    if (!actual().shouldShowLeadingZeros()) {
      fail("should show leading zeros");
    }
  }

  public void shouldntShowLeadingZeros() {
    if (actual().shouldShowLeadingZeros()) {
      fail("shouldn't show leading zeros");
    }
  }

  public void shouldPrefixSpaceForPositiveValues() {
    if (!actual().shouldPrefixSpaceForPositiveValues()) {
      fail("should prefix space for positive values");
    }
  }

  public void shouldntPrefixSpaceForPositiveValues() {
    if (actual().shouldPrefixSpaceForPositiveValues()) {
      fail("shouldn't prefix space for positive values");
    }
  }

  public void shouldPrefixPlusForPositiveValues() {
    if (!actual().shouldPrefixPlusForPositiveValues()) {
      fail("should prefix plus for positive values");
    }
  }

  public void shouldntPrefixPlusForPositiveValues() {
    if (actual().shouldPrefixPlusForPositiveValues()) {
      fail("shouldn't prefix plus for positive values");
    }
  }

  public void areValidFor(FormatChar formatChar) {
    if (!actual().areValidFor(formatChar)) {
      failWithActual("expected to be valid for", formatChar);
    }
  }

  public void areNotValidFor(FormatChar formatChar) {
    if (actual().areValidFor(formatChar)) {
      failWithActual("expected not to be valid for", formatChar);
    }
  }
}
