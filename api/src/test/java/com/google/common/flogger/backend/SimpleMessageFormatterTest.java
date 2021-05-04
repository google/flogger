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

package com.google.common.flogger.backend;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.context.Tags;
import com.google.common.flogger.testing.FakeLogData;
import com.google.common.flogger.testing.FakeMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleMessageFormatterTest {
  // TODO: More tests here (lots of SimpleMessageFormatter is tested by SimpleLogRecordTest).
  private static final MetadataKey<String> STRING_KEY = MetadataKey.single("string", String.class);
  private static final MetadataKey<Boolean> BOOL_KEY = MetadataKey.single("bool", Boolean.class);
  private static final MetadataKey<Integer> INT_KEY = MetadataKey.repeated("int", Integer.class);

  @Test
  public void testFormatFastPath() {
    // Just formatting a literal argument with no metadata avoids copying the message in a buffer.
    String literal = "Hello World";
    FakeLogData logData = FakeLogData.of(literal);
    assertThat(format(logData, Metadata.empty())).isSameInstanceAs(literal);

    // It also works if the log data has a "cause" (which is a special case and never formatted).
    Throwable cause = new IllegalArgumentException("Badness");
    logData.addMetadata(LogContext.Key.LOG_CAUSE, cause);
    assertThat(format(logData, Metadata.empty())).isSameInstanceAs(literal);

    // However it does not work as soon as there's any scope metadata.
    assertThat(format(logData, new FakeMetadata().add(INT_KEY, 42)))
        .isEqualTo("Hello World [CONTEXT int=42 ]");

    // Or if there's more metadata added to the log site.
    logData.addMetadata(BOOL_KEY, true);
    assertThat(format(logData, Metadata.empty())).isEqualTo("Hello World [CONTEXT bool=true ]");
  }

  // Parsing and basic formatting is well tested in BaseMessageFormatterTest.
  @Test
  public void testAppendFormatted() {
    FakeLogData logData = FakeLogData.withPrintfStyle("answer=%d", 42);
    assertThat(appendFormatted(logData, Metadata.empty())).isEqualTo("answer=42");

    FakeMetadata scope = new FakeMetadata().add(INT_KEY, 1);
    assertThat(appendFormatted(logData, scope)).isEqualTo("answer=42 [CONTEXT int=1 ]");

    Throwable cause = new IllegalArgumentException("Badness");
    logData.addMetadata(LogContext.Key.LOG_CAUSE, cause);
    assertThat(appendFormatted(logData, scope)).isEqualTo("answer=42 [CONTEXT int=1 ]");

    logData.addMetadata(INT_KEY, 2);
    assertThat(appendFormatted(logData, scope)).isEqualTo("answer=42 [CONTEXT int=1 int=2 ]");

    // Note that values are grouped by key, and keys are emitted in "encounter order" (scope first).
    scope.add(STRING_KEY, "Hello");
    assertThat(appendFormatted(logData, scope))
        .isEqualTo("answer=42 [CONTEXT int=1 int=2 string=\"Hello\" ]");

    // Tags get embedded as metadata, and format in metadata order. So while tag keys are ordered
    // locally, mixing tags and metadata does not result in a global ordering of context keys.
    Tags tags = Tags.builder().addTag("last", "bar").addTag("first", "foo").build();
    logData.addMetadata(LogContext.Key.TAGS, tags);
    assertThat(appendFormatted(logData, scope))
        .isEqualTo("answer=42 [CONTEXT int=1 int=2 string=\"Hello\" first=\"foo\" last=\"bar\" ]");
  }

  @Test
  public void testLogMessageFormatter() {
    LogMessageFormatter formatter = SimpleMessageFormatter.getDefaultFormatter();
    FakeLogData logData = FakeLogData.of("message");

    FakeMetadata scope = new FakeMetadata();
    scope.add(STRING_KEY, "Hello");
    Tags tags = Tags.builder().addTag("last", "bar").addTag("first", "foo").build();
    logData.addMetadata(LogContext.Key.TAGS, tags);

    MetadataProcessor metadata = MetadataProcessor.forScopeAndLogSite(scope, logData.getMetadata());
    assertThat(formatter.format(logData, metadata))
        .isEqualTo("message [CONTEXT string=\"Hello\" first=\"foo\" last=\"bar\" ]");
    assertThat(formatter.append(logData, metadata, new StringBuilder("PREFIX: ")).toString())
        .isEqualTo("PREFIX: message [CONTEXT string=\"Hello\" first=\"foo\" last=\"bar\" ]");
  }

  // As above but also ignore the STRING_KEY metadata.
  @Test
  public void testLogMessageFormatter_ignoreExtra() {
    LogMessageFormatter formatter = SimpleMessageFormatter.getSimpleFormatterIgnoring(STRING_KEY);
    FakeLogData logData = FakeLogData.of("message");

    FakeMetadata scope = new FakeMetadata();
    scope.add(STRING_KEY, "Hello");
    Tags tags = Tags.builder().addTag("last", "bar").addTag("first", "foo").build();
    logData.addMetadata(LogContext.Key.TAGS, tags);

    // Ignored in "simple" message formatting, should not appear in the output even though it's not
    // explicitly ignored above.
    Throwable cause = new IllegalArgumentException("Badness");
    logData.addMetadata(LogContext.Key.LOG_CAUSE, cause);

    MetadataProcessor metadata = MetadataProcessor.forScopeAndLogSite(scope, logData.getMetadata());
    assertThat(formatter.format(logData, metadata))
        .isEqualTo("message [CONTEXT first=\"foo\" last=\"bar\" ]");
    assertThat(formatter.append(logData, metadata, new StringBuilder("PREFIX: ")).toString())
        .isEqualTo("PREFIX: message [CONTEXT first=\"foo\" last=\"bar\" ]");
  }

  private static String format(LogData logData, Metadata scope) {
    MetadataProcessor metadata = MetadataProcessor.forScopeAndLogSite(scope, logData.getMetadata());
    return SimpleMessageFormatter.getDefaultFormatter().format(logData, metadata);
  }

  private static String appendFormatted(LogData logData, Metadata scope) {
    MetadataProcessor metadata = MetadataProcessor.forScopeAndLogSite(scope, logData.getMetadata());
    return SimpleMessageFormatter.getDefaultFormatter()
        .append(logData, metadata, new StringBuilder())
        .toString();
  }
}
