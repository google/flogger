# Flogger Best Practices


{:toc}

## When possible, log objects, not strings {#structured}

It's good to remember that a logging backend might not necessarily be outputting
only plain text files. For a statement such as

```java
logger.atInfo().log("Received message is: %s", proto)
```

the backend has the opportunity to do more interesting things with the data in
its original, structured form.

On the other hand, with either of these calls:

```java
logger.atInfo().log("Received message is: %s", proto.toString())
logger.atInfo().log("Received message is: " + proto);
```

that opportunity is lost.

Of course, you may know that you're not using any advanced structured logging
features at the moment, but if it's not causing any problems, we still recommend
this as a good rule of thumb for logging hygiene.

## Avoid doing work at log sites {#avoid-work}

Flogger is designed to make disabled logging statements virtually free, so that
more of them can be left intact in the code without harm. This is a great
advantage, but unfortunately it can be defeated easily:

```java
logger.atFine().log("stats=%s", createSummaryOf(stats));
```

`createSummaryOf` will now be called every time, regardless of configured log
levels or rate limiting.

Here's how to fix this problem, **in order of preferrence**.

### 1. Use `lazy` (Java 8+):

Import this method from the [`LazyArgs`] class.

```java
// Almost no work done at the log site and structure is preserved.
logger.atFine().log("stats=%s", lazy(() -> createSummaryOf(stats)));
```

With this simple change, almost no work is done at the log site (just instance
creation for the lambda expression). Flogger will only evaluate this lambda
if it intends to actually log the message.

### 2. Use the `toString()` method of the logged value:

If `stats.toString()` happens to produce the text you want, you can make use of
it like this:

```java
// No work done at the log site, but structure is lost.
logger.atFine().log("stats=%s", stats);
```

You might even *create* a wrapper so as to have the `toString` behavior you want:

```java
// Almost no work done at the log site, but structure is lost.
logger.atFine().log("stats=%s", new MyNewStatsFormatter(stats));
```

Note that either approach removes structure from the logged value and is only
suitable in cases where the value being logged is "string like". For structured
values (e.g. protocol buffer messages), prefer `lazy()`.

### 3. Guard the log statement using `isEnabled()`:

Avoid this last-resort option if at all possible.

```java
// No work done for disabled log statements, but code is less maintainable.
// The "guarding" log level must be kept in sync with the "logging" level.
if (logger.atFine().isEnabled()) {
  Foo foo = doComplexThing();
  Bar bar = doOtherComplexThing();
  Statistics stats = calculateStatsFrom(foo, bar);
  logger.atFine().log("stats=%s", createSummaryOf(stats));
}
```

Note also that this approach only guards log statements by level and does not
help with rate-limited log statements.


[`LazyArgs`]: https://github.com/google/flogger/blob/master/api/src/main/java/com/google/common/flogger/LazyArgs.java

## Don't be afraid to add fine-grained logging to your code {#fine-grained}

Consider:

```java
for (Thing t : things) {
  int status = process(t);
  logger.atFinest().log("processed %s, status=%x", t, status);
}
```

The log statement may be essential in debugging an important issue, but will it
slow down the (possibly time critical) loop?

With Flogger, log statements such as the one above will never cause an object
allocation to occur when they are disabled. No varargs arrays are created (up to
10 parameters) and no auto-boxing is necessary (up to 2 parameters).

Hot-spot compilation of this code should result in the cost of the disabled log
statement being immeasurably small.

Note that when you want to enable fine-grained logging in production, you should
do it selectively for only the classes or packages you are concerned with.
Otherwise, the enormous number of messages actually being logged could run you
out of memory (and it's not the developers' fault for adding all those log
statements!).

## Use string literals for your log messages and avoid string concatenation {#literals}

Even if you are unconcerned with "avoiding work" or with structured logging,
there are still other reasons to avoid using string concatenation to create your
log message.

*   It separates safe string literals from possible user data, which might need
    filtering (e.g., if it contains Personally Identifiable Information).
*   Flogger can cache the result of parsing a message template if the log
    message was a string literal.

## Don't pass loggers between classes {#one-per-class}

Only log via a single static logger instance that's created inside the same
source file as the log statement.

There is generally no reason to pass a logger from one class to another. Note in
particular that this does **not** cause the name of the class that created the
logger to appear in log output. It does have the effect of using the passed
logger's configuration, but this is confusing and should be unnecessary.
(Perhaps such code should instead throw an exception, after which the caller can
catch and log it in the normal way?)


## Don't create a `Throwable` just to log it {#stack-trace}

There is no need to do this:

```java
logger.atInfo().withCause(new Exception()).log("Message");
```

You should do this instead:

```java
logger.atInfo().withStackTrace(<SIZE>).log("Message");
```

where `<SIZE>` is one of the [`StackSize`] enum constants, `SMALL`, `MEDIUM`,
`LARGE` or `FULL`.

A stack trace generated by `withStackTrace()` will show up as a
`LogSiteStackTrace` exception in the default `java.util.logging` backend. Other
backends may choose to handle this differently though.

[`StackSize`]: https://github.com/google/flogger/blob/master/api/src/main/java/com/google/common/flogger/StackSize.java

## Make the logger the first static field in a class {#first-field}

The following code fails with a `NullPointerException`:

```java
private static final Data data = initStaticData();
private static final FluentLogger logger = FluentLogger.forEnclosingClass();

private static Data initStaticData() {
  logger.atInfo().log("Initializing static data");
  ...
}
```

When `initStaticData()` is called, the static `logger` field is still `null`. A
habit of initializing your logger *first* will prevent this problem.

## Don't log and throw {#log-and-throw}

When throwing an exception, let the surrounding code choose whether to log it.
When you log it yourself first, it often ends up being logged multiple times,
creating the misleading impression that multiple issues need investigating.

## Use `LoggerConfig`, not a JDK `Logger`, to configure logging {#weak-refs}

A call such as:

```java
Logger.getLogger(loggerName).setLevel(Level.FINE);
```

May end up having *no effect*. Since this code is not retaining any reference to
the logger, it may be garbage collected at any time, erasing your configuration
choices. Instead, use `LoggerConfig`, which retains strong references to each
logger it accesses.

```java
LoggerConfig.of(logger).setLevel(Level.FINE);
```

See [`LoggerConfig`] for details.

[`LoggerConfig`]: https://github.com/google/flogger/blob/master/api/src/main/java/com/google/common/flogger/LoggerConfig.java

## Don't split log statements {#no-split}

Flogger's "fluent" API is designed for log statements to exist as a single
statement. The `Api` instance returned by a logger is not safe to use on its
own.

```java {.bad}
// **** NEVER DO THIS ****
GoogleLogger.Api api = logger.atInfo();
...
api.log("message");
```

Splitting a log statement causes several issues such as:

* Incorrect timestamps in log statements
* Incorrect or even broken log site injection
* Errors due to accidental reuse (the `Api` is a one-use instance)
* Errors due to concurrent logging in different threads

Flogger's API is designed to never need you to split the `Api` out like this,
so if you think you really need to do it, please contact g/flogger-discuss.

One misconception is that you need to do this to make conditional calls on
fluent methods, such as:

```java {.bad}
// **** NEVER DO THIS ****
GoogleLogger.Api api = logger.atInfo();
if (wantRateLimiting) {
  api.atMostEvery(5, SECONDS);
}
api.log("message");
```

This is never needed, since any fluent methods expected to be conditional accept
"no-op" parameters. The below examples have no effect in log statements:

*   `atMostEvery(0, unit)`
*   `every(1)`
*   `withCause(null)`
*   `withStackTrace(StackSize.NONE)`

Thus the above example can be written as:

```java {.good}
logger.atInfo()
    .atMostEvery(wantRateLimiting ? 5 : 0, SECONDS)
    .log("message");
```

Or you can add a helper method to return the log period if used in many places.
