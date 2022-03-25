# Benefits

{:toc}

## Readability {#readability}

While, in its simplest form, a fluent log statement takes slightly more
characters to write, it permits a far greater functionality and will hopefully
lead to more readable and expressive log statements.

When non-fluent loggings APIs add new functionality, it is often achieved by
creating overloads of existing methods with additional parameters, or adding new
methods with distinct names. In either case this tends to create a more complex
API surface with more chance for confusion and misuse.

For example, the JDK logger has additional methods to facilitate passing in a
`Throwable` cause, but those methods do not permit passing a separate parameter
list, and are easily confused with the log method which takes a single
parameter. The method:

```java
logger.log(INFO, "message...", cause);
```

will behave very differently depending on whether `cause` is a `Throwable` or
not. An analysis of Java logging code within Google showed many thousands of
cases where complexity and ambiguity in logging APIs resulted in misuse.

Another problem with the simple `log.info(...)` form is that as soon as logging
needs to be made conditional you are required to add an 'if' clause around it.
This adds to code "clutter" and introduces more chance for simple errors.

Consider the difference between:

```java
private static final AtomicInteger logCounter = new AtomicInteger();
...
if ((logCounter.incrementAndGet() % 100) == 0) {
  logger.info("My log message {0} [every 100]", arg);
}
```

and

```java
logger.atInfo().every(100).log("My log message %s", arg);
```

This becomes especially clear when you realize that in the first example, a new
counter (and associated field) would be required for each distinct throttled log
statement.

## Performance {#performance}

Flogger has been designed and implemented for high performance logging. By
building a set of carefully constructed APIs, both frontend and backend, Flogger
permits multiple backend implementations to be seamlessly plugged in to provide
the best possible performance.

## Extensibility {#extensibility}

While we expect that the core Flogger API would provide almost all commonly used
functionality, there will always be cases where a team has a special requirement
that is not covered. In this case it is possible to locally extend the logging
API and add methods in the fluent chain.

For example, consider a mechanism for emitting per-user log statements
which get written out separately from the main logs. Currently this requires a
separate supporting class. With Flogger a `UserLogger` class could be written
with an extended API:

```java
logger.at(INFO).forUserId(id).log("Message: %s", param);
```

## Reducing the cost of disabled log statements {#cheap-disabled-logging}

The simple `log.info(String, Object...)` approach to logging is concise at the
source code level, but can introduce surprising cost in bytecode. Vararg methods
require a new `Object[]` to be allocated and filled before the method can be
invoked. Additionally any fundamental types passed in must be auto-boxed. This
all costs additional bytecode and latency at the call site and is particularly
unfortunate if the log statement isn't actually enabled.

There are ways to work around this but they would require hundreds of additional
method overrides to be present. See [anatomy of an API](anatomy.md) for more
details.
