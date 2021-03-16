# Flogger: A Fluent Logging API for Java
[![Maven Central](https://img.shields.io/maven-central/v/com.google.flogger/flogger.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.google.flogger%22%20AND%20a:%22flogger%22&core=gav) [![Javadocs](https://javadoc.io/badge/com.google.flogger/flogger.svg)](https://javadoc.io/doc/com.google.flogger/flogger) [![CI](https://github.com/google/flogger/workflows/CI/badge.svg?branch=master)](https://github.com/google/flogger/actions)

## What is it?

Flogger is a [fluent](http://en.wikipedia.org/wiki/Fluent_interface) logging API
for Java. It supports a wide variety of features, and has **[many benefits]**
over existing logging APIs.

Come for more self-documenting log statements:

```java
logger.atInfo().withCause(exception).log("Log message with: %s", argument);
```

Stay for additional features that help you manage your logging better:

```java
logger.atSevere()
    .atMostEvery(30, SECONDS)
    .log("Value: %s", lazy(() -> doExpensiveCalculation()));
```

## Benefits

While some users prefer "fluency" as a style, this is not what the argument for
Flogger rests on. Flogger offers these key, concrete advantages over other
logging APIs:

*   Logging at disabled levels is [effectively free]. Finally, you can add as
    many fine-grained log statements to your code as you want, without worry.
*   Flogger also has very high [performance] for enabled log statements.
*   A fluent API accommodates a variety of [present and future
    features][extensibility] without combinatorial explosion, and without
    requiring separate logging façades.
*   Less reliance on long parameter lists makes it harder to misuse and yields
    more [self-documenting][readability] code.

## Yet another logging API?

The field of open-source Java logging APIs is already extremely crowded, so why
add another?

To paraphrase Douglas Adams "Google's codebase is big. Really big. You just
won’t believe how vastly hugely [mind-bogglingly
big](https://cacm.acm.org/magazines/2016/7/204032-why-google-stores-billions-of-lines-of-code-in-a-single-repository)
it is". Inevitably this resulted in many different debug logging APIs being used
throughout the Java codebase, each with its own benefits and issues. Developers
were forced to switch between APIs as they worked on different projects, and
differences between APIs caused confusion and bugs.

Flogger is the result of an attempt to create a unified logging API, suitable
for the vast majority of Java projects in Google.

For something of this magnitude it would have been preferable to use an
existing logging API, rather than creating and maintaining our own. However, the
Java Core Libraries Team (i.e. Guava maintainers) concluded that Flogger was not
slightly better than the alternatives, but much better.

By switching the majority of Java code in Google to use Flogger, many thousands
of bugs have been fixed and the cost to developers of learning new logging APIs
as they move through the codebase has been eliminated. Flogger is now the sole
recommended Java logging API within Google.

## How to use Flogger

### 1. Add the dependencies on Flogger

All code that uses flogger should depend on
`com.google.flogger:flogger:<version>` and
`com.google.flogger:flogger-system-backend:<version>`.

> Note: the dependency on `flogger-system-backend` is only required to be
included when the binary is run. If you have a modularized build, you can
include this dependency by the root module that builds your app/binary, and can
be `runtime` scope.

<!-- TODO(dbeaumont): link to docs for how to specify a backend. -->

### 2. Add an import for [`FluentLogger`]

```java
import com.google.common.flogger.FluentLogger;
```

### 3. Create a `private static final` instance

```java
private static final FluentLogger logger = FluentLogger.forEnclosingClass();
```

### 4. Start logging:

```java
logger.atInfo().withCause(exception).log("Log message with: %s", argument);
```

Log messages can use any of [Java's printf format
specifiers](https://docs.oracle.com/javase/9/docs/api/java/util/Formatter.html);
such as `%s`, `%d`, `%016x` etc.

Note that you may also see code and documentation that references the
`GoogleLogger` class. This is a minor variant of the default `FluentLogger`
designed for use in Google's codebase. The `FluentLogger` API is recommended for
non-Google code, since its API should remain more stable over time.

<a name="more-information"></a>
## More information

Flogger was designed and implemented by David Beaumont, with invaluable help
from the Java Core Libraries Team and many other Googlers.

If you interested in a deeper dive into the rationale behind Flogger's API,
please see [Anatomy of an API][anatomy].

*   [Stack Overflow](https://stackoverflow.com/questions/ask?tags=flogger)
*   [Mailing list](https://groups.google.com/forum/#!forum/flogger-discuss)
*   [File a bug](https://github.com/google/flogger/issues)

[anatomy]: https://google.github.io/flogger/anatomy
<!-- TODO(ronshapiro): publish javadoc, and point to that instead of source files -->
[backend]: https://github.com/google/flogger/blob/master/api/src/main/java/com/google/common/flogger/backend/LoggerBackend.java
[effectively free]: https://google.github.io/flogger/benefits#cheap-disabled-logging
[extensibility]: https://google.github.io/flogger/benefits#extensibility
[`FluentLogger`]: https://github.com/google/flogger/blob/master/api/src/main/java/com/google/common/flogger/FluentLogger.java
[many benefits]: https://google.github.io/flogger/benefits
[performance]: https://google.github.io/flogger/benefits#performance
[readability]: https://google.github.io/flogger/benefits#readability
