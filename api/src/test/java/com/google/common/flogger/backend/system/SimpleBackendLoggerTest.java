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

package com.google.common.flogger.backend.system;

import static com.google.common.flogger.testing.FakeLogData.withBraceStyle;
import static com.google.common.flogger.testing.FakeLogData.withPrintfStyle;
import static com.google.common.truth.Truth.assertThat;
import static java.util.logging.Level.INFO;
import static org.junit.Assert.fail;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.parser.ParseException;
import com.google.common.flogger.testing.AssertingLogger;
import com.google.common.flogger.testing.FakeLogData;
import java.util.Calendar;
import java.util.Formattable;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleBackendLoggerTest {
  private static LoggerBackend newBackend(Logger logger) {
    return new SimpleLoggerBackend(logger);
  }

  @Test
  public void testLiteralArgument() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(FakeLogData.of("Hello %n %d %% World"));
    backend.log(FakeLogData.of("Hello '{0}' World"));

    logger.assertLogCount(2);
    logger.assertLogEntry(0, INFO, "Hello %n %d %% World");
    logger.assertLogEntry(1, INFO, "Hello '{0}' World");
  }

  @Test
  public void testSingleArgument() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(withPrintfStyle("Hello %s World", "Printf"));
    backend.log(withBraceStyle("Hello {0} World", "Brace Format"));

    logger.assertLogCount(2);
    logger.assertLogEntry(0, INFO, "Hello Printf World");
    logger.assertLogEntry(1, INFO, "Hello Brace Format World");
  }

  @Test
  public void testMultipleArguments() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(withPrintfStyle("Printf: %s %s %s", "Foo", "Bar", "Baz"));
    backend.log(withBraceStyle("Brace: {0} {1} {2}", "Foo", "Bar", "Baz"));

    logger.assertLogCount(2);
    logger.assertLogEntry(0, INFO, "Printf: Foo Bar Baz");
    logger.assertLogEntry(1, INFO, "Brace: Foo Bar Baz");
  }

  @Test
  public void testDefaultFormatting() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    Calendar cal = new GregorianCalendar(1985, 6, 13, 5, 20, 3);
    cal.setTimeZone(TimeZone.getTimeZone("GMT"));

    backend.log(
        withPrintfStyle("int=%d, float=%f, date=%3$tD %3$tr", 12345678, 1234.5678D, cal));
    backend.log(
        withBraceStyle("int={0}, float={1}, date={2}", 12345678, 1234.5678D, cal));

    // Note that the "%f" and "{n}" formatting for floats differ, but that matches String.format()
    // and MessageFormat respectively.
    logger.assertLogCount(2);
    logger.assertLogEntry(0, INFO, "int=12345678, float=1234.567800, date=07/13/85 05:20:03 AM");
    logger.assertLogEntry(
        1, INFO, "int=12,345,678, float=1,234.567800, date=Sat Jul 13 05:20:03 GMT 1985");
  }

  @Test
  public void testPrintfWithFormattedArguments() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(withPrintfStyle("Hello %#08X %+,8d %8.2f World", 0xcafe, 1234, -12.0));

    logger.assertLogCount(1);
    logger.assertLogEntry(0, INFO, "Hello 0X00CAFE   +1,234   -12.00 World");
  }

  @Test
  public void testPrintfGeneralValue() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(withPrintfStyle("Hello %s World", "anything"));

    logger.assertLogCount(1);
    logger.assertLogEntry(0, INFO, "Hello anything World");
  }

  @Test
  public void testPrintfFormattable() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    Object arg =
        new Formattable() {
          @Override
          public void formatTo(java.util.Formatter fmt, int flags, int width, int precision) {
            fmt.format("[f=%d, w=%d, p=%d]", flags, width, precision);
          }

          @Override
          public String toString() {
            return "FAILED";
          }
        };

    backend.log(withPrintfStyle("Hello %s World", arg));
    backend.log(withPrintfStyle("Hello %#S World", arg));
    backend.log(withPrintfStyle("Hello %-10.4s World", arg));

    logger.assertLogCount(3);
    logger.assertLogEntry(0, INFO, "Hello [f=0, w=-1, p=-1] World");
    logger.assertLogEntry(1, INFO, "Hello [f=6, w=-1, p=-1] World");
    logger.assertLogEntry(2, INFO, "Hello [f=1, w=10, p=4] World");
  }

  @Test
  public void testPrintfDateTime() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    // Use "May" as the month here since some JDK setups render other month abbreviations
    // differently (e.g. "Jul" vs "July") , and "May" is short enough to never need abbreviation.
    // This isn't great, and the right thing would be to test against a value derived from the
    // current system in which the test is being run.
    Calendar cal = new GregorianCalendar(1985, 4, 13, 5, 20, 3);
    cal.setTimeZone(TimeZone.getTimeZone("GMT"));

    backend.log(withPrintfStyle("Day=%1$Ta %1$te, Month=%1$tB, Year=%1$tY", cal));
    backend.log(withPrintfStyle("Time=%1$tl:%1$tM:%1$tS %1$Tp", cal));
    // String should format to 28 characters, so set width 30 to test padding.
    backend.log(withPrintfStyle("%-30tc", cal));
    // Use one of the formats which doesn't require time-zone interaction for testing millis
    // (otherwise this test would not be system portable).
    backend.log(withPrintfStyle("Seconds=%tS", cal.getTimeInMillis()));

    logger.assertLogCount(4);
    logger.assertLogEntry(0, INFO, "Day=MON 13, Month=May, Year=1985");
    logger.assertLogEntry(1, INFO, "Time=5:20:03 AM");
    logger.assertLogEntry(2, INFO, "Mon May 13 05:20:03 GMT 1985  "); // padded
    logger.assertLogEntry(3, INFO, "Seconds=03");
  }

  @Test
  public void testPrintfHashcode() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    Object arg =
        new Object() {
          @Override
          public int hashCode() {
            return 0xDEADBEEF;
          }
        };

    backend.log(withPrintfStyle("hash=%h", arg));
    backend.log(withPrintfStyle("%-10H", arg));

    logger.assertLogCount(2);
    logger.assertLogEntry(0, INFO, "hash=deadbeef");
    logger.assertLogEntry(1, INFO, "DEADBEEF  "); // padded
  }

  @Test
  public void testLoggingWithNull() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    // Typed variable is needed to disambiguate log(String, Object) and log(String, Object[]).
    Object nullArg = null;
    // %h and %t trigger different types of parameters, so it's worth checking them as well.
    backend.log(withPrintfStyle("[%6.2f] #1", nullArg));
    backend.log(withPrintfStyle("[%-10H] #2", nullArg));
    backend.log(withPrintfStyle("[%8tc] #3", nullArg));
    backend.log(withBraceStyle("[{0}] #4", nullArg));

    logger.assertLogCount(4);
    logger.assertLogEntry(0, INFO, "[null] #1");
    logger.assertLogEntry(1, INFO, "[null] #2");
    logger.assertLogEntry(2, INFO, "[null] #3");
    logger.assertLogEntry(3, INFO, "[null] #4");
  }

  @Test
  public void testLoggingPrintfWithSystemNewline() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    String nl = System.getProperty("line.separator");
    assertThat(nl).containsMatch("\\n|\\r(?:\\n)?");

    backend.log(withPrintfStyle("Hello %d%n World%n", 42));

    logger.assertLogCount(1);
    logger.assertLogEntry(0, INFO, "Hello 42" + nl + " World" + nl);
  }

  @Test
  public void testErrorHandling() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    LogData data = withPrintfStyle("Hello %?X World", "ignored");
    try {
      backend.log(data);
      fail("expected ParseException");
    } catch (ParseException expected) {
      logger.assertLogCount(0);
      backend.handleError(expected, data);
      logger.assertLogCount(1);
      assertThat(logger.getMessage(0)).contains("lo %[?]X Wo");
    }
  }

  @Test
  public void testOutOfOrderArguments() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(withPrintfStyle("Printf: %3$d %2$d %1$d", 1, 2, 3));
    backend.log(withBraceStyle("Brace: {2} {1} {0}", 1, 2, 3));

    logger.assertLogCount(2);
    logger.assertLogEntry(0, INFO, "Printf: 3 2 1");
    logger.assertLogEntry(1, INFO, "Brace: 3 2 1");
  }

  @Test
  public void testRepeatedArguments() {
    AssertingLogger logger = new AssertingLogger();
    LoggerBackend backend = newBackend(logger);

    backend.log(withPrintfStyle("Printf: %1$d %1$d %1$d", 23));
    backend.log(withPrintfStyle("Printf: %d %<d %<d", 42));
    backend.log(withBraceStyle("Brace: {0} {0} {0}", 42));

    logger.assertLogCount(3);
    logger.assertLogEntry(0, INFO, "Printf: 23 23 23");
    logger.assertLogEntry(1, INFO, "Printf: 42 42 42");
    logger.assertLogEntry(2, INFO, "Brace: 42 42 42");
  }
}
