# Anatomy of a logging API

{:toc}

This page is quite detailed and goes into depth regarding some of the decisions
made during the design of the Flogger fluent API.

A typical log statement in an existing logging API might be something like:

```java
logger.info("My message: {0}", arg);
```

and the equivalent statement in Flogger's API would be:

```java
logger.atInfo().log("My message: %s", arg);
```

At first sight, Flogger's API looks somewhat verbose; after all what's wrong
with having a single method to do the logging? Undeniably the first version is
shorter and the Flogger log statement even contains a second method invocation.
However the shorter form of the log statement hides some non-trivial issues,
which the fluent approach of Flogger's API can overcome. Before examining
Flogger's API, it's worth looking at the 'classic' logging API in a little more
depth.

## The classic logging API {#classic-api}

Typically a logging API contains methods that look something like:

```java
level(String);
level(String, Object)
level(String, Object...)
```

where `level` can be one of about seven log level names (usually `severe`,
`warning`, `config`, `info`, `fine`, `finer`, `finest`) as well as having a
canonical `log` method which accepts an additional log level.

In addition to this there are usually variants of the methods that take a
`cause` (a `Throwable` instance) that is associated with the log statement.
Something like:

```java
level(Throwable, String);
level(Throwable, String, Object)
level(Throwable, String, Object...)
```

When you examine this approach to the logging API it's quite easy to see that
the API is trying to do three distinct things in one method call:

*   Specify the log level (via the choice the method itself)
*   Optionally attach metadata to the log statement (the `cause`)
*   Specify the log message and arguments

This approach quickly multiplies the number of different logging methods needed
to satisfy these independent concerns and we are quickly left with dozens of
methods in our logging class. To see why this can cause a bit of trouble it's
worth examining the real cost of making a logging call in the general case.

Ignoring for a moment the specification of a cause, the most general form of a
log statement is:

```java
level(String, Object... )
```

which will be used for any situation in which several arguments are specified.

Let's consider a simple logging API that uses varargs:

```java
public class Logger {
  void log(String message, Object... args) {
    // Do logging...
  }
}
```

and a simple class that uses it:

```java
public class MyClass {
  private static final Logger logger = new Logger();
  public int getFoo() {
    return 23;
  }
  public String getBar() {
    return "Hello World";
  }
  public void myMethod() {
    logger.log("Foo[%d] = %s", getFoo(), getBar());
  }
}
```

When we look at the bytecode produced for the single line of code that makes up
the logging statement we see:

```java
// Load the logger instance from the static field onto the top of the stack.
GETSTATIC com/example/MyClass.logger : Lcom/example/Logger;
// Push the constant string onto the top of the stack.
LDC "Foo[%d] = %s"
// Create an Object[] of size 2 to hold the logging arguments.
ICONST_2
ANEWARRAY java/lang/Object
DUP
// Call getFoo() and store the result at index 0 in the array
// (note that because getFoo() returns a fundamental type, we must 'box' it
// into an Integer instance before storing it in the array).
ICONST_0
ALOAD 0
INVOKEVIRTUAL com/example/MyClass.getFoo()I
INVOKESTATIC java/lang/Integer.valueOf(I)Ljava/lang/Integer;
AASTORE
DUP
// Call getBar() and store the result at index 1 in the array.
ICONST_1
ALOAD 0
INVOKEVIRTUAL com/example/MyClass.getBar()Ljava/lang/String;
AASTORE
// Invoke the logging method.
INVOKEVIRTUAL com/example/Logger.log(Ljava/lang/String;[Ljava/lang/Object;)V
```

In this code there is one place where a new Java instance must be allocated
(`ANEWARRAY`), and one place where a new instance might be allocated (the call
to `Integer.valueOf(int)`), both of which are hidden from the user by the
compiler through the use of varargs and auto-boxing respectively.

In fact this code is essentially no different from having the `log()` method be:

```java
void log(String message, Object[] args) {
  // Do logging...
}
```

and forcing the caller to deal with packaging up the arguments into the
`Object[]` themselves; it's simply shorter and easier on the eye.

The problem with this code is not that it exists (at some point during the
processing of a log statement you are going to do far more work than is shown
here), but that it exists in the caller's code. This means that even when log
statements are disabled, and even if the log method itself did nothing
whatsoever, this code would be required to be executed first.

From analysis of logging behaviour in large applications in Google, it seems
that disabled log statements are hit many orders of magnitude more than enabled
ones. This is not unexpected, since "finer" logging tends to be put into inner
loops. Thus for every log that your code emits, it's expected that hundreds or
even thousands of disabled log statements will have been encountered.

## Avoiding Varargs {#avoiding-varargs}

One common solution to this is to provide an interrogative method on the logging
API to determine whether logging is enabled for a given log level. However while
this is useful in some situations, it is typically not used for simple log
statements that just happen to use varargs. Ideally we would like to do no work
at all until we have determined that logging is enabled.

Another approach is to provide overloads of the logging methods in order to
postpone the need to do this work until after a check has been made. Now our
logger looks something like:

```java
public class Logger {
  // Most generic version of the log method.
  void log(String message, Object... args) {
    if (shouldLog()) {
      logImpl(message, args);
    }
  }
  // Lots of overrides to avoid varargs and auto-boxing.
  void log(String message, int a, Object b) {
    if (shouldLog()) {
      // This is where we pay the cost of varargs and auto-boxing.
      logImpl(message, a, b);
    }
  }
  // Canonical log method which uses varargs.
  void logImpl(String message, Object... args) {
    // Do logging...
  }
  boolean shouldLog() {
    // Determine whether logging is enabled.
  }
}
```

But our calling code remains (visually) the same:

```java
public void myMethod() {
  logger.log("Foo[%d] = %s", getFoo(), getBar());
}
```

and the bytecode produced for it is now:

```
// Load the logger instance from the static field onto the top of the stack.
GETSTATIC com/example/MyClass.logger : Lcom/example/Logger;
// Push the constant string onto the top of the stack.
LDC "Foo[%d] = %s"
// Call getFoo() to get the first argument onto the stack.
ALOAD 0
INVOKEVIRTUAL com/example/MyClass.getFoo()I
// Call getBar() to get the second argument onto the stack.
ALOAD 0
INVOKEVIRTUAL com/example/MyClass.getBar()Ljava/lang/String;
// Invoke the logging method.
INVOKEVIRTUAL com/example/Logger.log(Ljava/lang/String;ILjava/lang/Object;)V
```

which is considerably shorter than before and, importantly, contains no new
Object allocations.

So, if we want to minimize the calling cost of logging when it's disabled, all
we need to do is add overrides to all the log methods, right? Ok, so lets work
out how many additional methods we might need...

If we want to avoid the cost of allocating a varargs array (which is something
we must always do when using varargs) then we can override the log method as
follows:

```java
void log(String message, Object arg1);
void log(String message, Object arg1, Object arg2);
void log(String message, Object arg1, Object arg2, ... , Object argN);
```

for some reasonable maximum `N`.

However, if we also wish to avoid auto-boxing a single argument, we will also
need:

```java
void log(String message, char arg1);
void log(String message, long arg1);
void log(String message, double arg1);
```

Where `byte`, `short` and `int` will be promoted to `long`, and `float` will be
promoted to `double`. However this does not work if you want to support any
non-trivial formatting options.

For example if the logger supports the `printf` style for formatting arguments,
you will need to support `%x` (hexadecimal) formatting which operates on the
unsigned value of the argument. Specifically a statement such as:

```java
log("Byte: %#X", (byte) 0xff);
```

must result in the output `Byte: 0xFF`, and not `Byte: 0x00000000000000FF`,
which would be the case if the value had been promoted to a `long`.

Thus, if we provide an overload for `long`, we need to provide overloads for
`byte`, `short` and `int`. We also need to overload for `char` because a `char`
is promoted to an `int` in preference to being boxed as a `Character`.

Similarly, if we provide an overload for `double` we should technically provide
an overload for `float` (the string representation of a `float` can differ to
that of `double`).

Looking at real world usage of log statements however we see that integral types
(`byte`, `short`, `int`, `long`) are logged about 35-times more frequently than
floating point types. So if we assume that any logger wishing to avoid common
auto-boxing would need to, at least, support overloads for integral types, then
we need the following overloads:

```java
void log(String message, char arg);
void log(String message, byte arg);
void log(String message, short arg);
void log(String message, int arg);
void log(String message, long arg);
```

This brings the total number of overrides needed to `(N + 5)` (where `N` was
the number of additional methods required to avoid varargs array allocation).

If we now go back and consider applying this technique to avoid varargs and
auto-boxing in our original logging API, we need to multiply the number of
methods we have by this factor:

```
#log-methods = ((#log-levels + 1) * 2) * (N + 5)
```

The `+ 1` accounts for the expected existence of a canonical `log()` method
which accepts an explicit level, and the `* 2` accounts for the alternate
versions of the log methods that take a `Throwable` as a `cause`.

So where `#log-levels = 7` (as it does for many common logging APIs) and we
assume that a reasonable expected maximum number of arguments that we wish to
avoid varargs for is 8, then our traditional logging API will need **208 method
overrides** to avoid auto-boxing for just one parameter.

Fundamentally this is being caused by the fact that (as mentioned at the
beginning) the original API was trying to do 3 distinct things, each of which
adds a multiplicative factor to the number of methods in the API. The critical
observation here is that things would be a lot simpler if we only had to deal
with methods related to the specification of the log message and its parameters
in the log method itself. If we can separate these 3 concerns, we can create a
more orthogonal API to which we can apply the technique of method overriding for
efficiency without a combinatorial explosion.

## The Fluent Logging API {#fluent-api}

Flogger's API is designed to require only a single conceptual log method in its
API. It does this by having a fluent call chain from which logging statements
can be built. Returning to the original example of a Flogger log statement we
can now see why it's important to have two methods in the chain:

```java
logger.atInfo().log("My message: %s", arg);
```

The `atInfo()` method returns a logging API which contains (conceptually at
least) a single logging method called (somewhat unimaginatively) `log()`.
Additionally if we need to specify a cause, we can do so by chaining an
additional method call into the log statement:

```java
logger.atInfo().withCause(e).log("My message: %s", arg);
```

And with only a single `log()` method in our API it becomes perfectly feasible
to add overrides to avoid things like varargs and auto-boxing as we now only
need a small number of overrides:

```
#log-methods = (N + 5)
```

Now we can start to see how this fluent logging API generalizes even further by
letting us add other methods to the call chain. Examples include:

```java
// Log 1 in 100 messages to avoid spamming the logs.
logger.atInfo().every(100).log("My message", args);
// Log at most once every 5 seconds.
logger.atInfo().atMostEvery(5, SECONDS).log("My message", args);
```

In the general case, a fluent log statement can be thought of as:

```java
logger.<level-selector>.<extensible-API-methods>.<terminal-log-statement>
```

You might be asking how this can be more efficient that a single log call. After
all the level selector must return an instance of something (a context for the
log statement) which could be modified by any of the following methods, so
surely there must still be a memory allocation per log statement (which obviates
some of the gains made from avoiding allocations for varargs).

However it's important to realize that in the case of varargs we were only
trying to avoid the allocation of the array in the case that logging was
disabled. If logging is enabled, the allocation of a small instance to hold
contextual information for the log statement is not significant compared to the
work we will do later.

Fortunately we know whether or not logging is disabled at the point that the
level selector was called (and this is always the first thing we do). So if
logging is disabled we can chose to return a different implementation of the
logging context which simply discards all its arguments for every subsequent
method call (a "No-Op" instance). Conveniently this instance is naturally
immutable and thread safe, so we can return the same singleton instance every
time, which avoids an allocation when logging is disabled.

This means that as well as being a more functional API, using a fluent API means
that in the vast majority of cases a disabled log statement can avoid needing to
allocate anything.

## Summary {#summary}

In summary, the use of a fluent API for specifying log statements makes it
possible to design an API that avoids any work when log statements are disabled.
