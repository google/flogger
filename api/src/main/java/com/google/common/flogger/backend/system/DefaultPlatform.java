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

package com.google.common.flogger.backend.system;

import static com.google.common.flogger.util.StaticMethodCaller.callGetterFromSystemProperty;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.context.ContextDataProvider;

/**
 * The default fluent logger platform for a server-side Java environment. The default platform
 * implements the following behavior:
 * <ul>
 *   <li>It generates {@code SimpleLoggerBackend} logger backends.
 *   <li>It uses a default clock implementation (only millisecond precision until Java 8).
 *   <li>It does not provide support for injecting additional metadata into log statements.
 *   <li>It determines call site information via stack analysis.
 * </ul>
 *
 * <p>This class is designed to allow configuration via system properties. Each aspect of the
 * platform is configured by providing the name of a static method, in the form
 * {@code "<package>.<class>#<method>"}, which returns an instance of the appropriate type.
 *
 * The namespace for system properties is:
 * <ul>
 *   <li>{@code flogger.backend_factory}: Provides an instance of
 *       {@code com.google.common.flogger.backend.system.BackendFactory}.
 *   <li>{@code flogger.logging_context}: Provides an instance of
 *       {@code com.google.common.flogger.context.ContextDataProvider}.
 *   <li>{@code flogger.clock}: Provides an instance of
 *       {@code com.google.common.flogger.backend.system.Clock}.
 * </ul>
 *
 * <p>Note that if the {@code com.google.flogger:flogger-grpc-context} is also available and the
 * value of the {@code flogger.logging_context} system property is
 * {@code "com.google.common.flogger.grpc.GrpcContextDataProvider#getInstance"} then a gRPC based
 * implementation of the {@code ScopedLoggingContext} API will be used.
 */
// Non-final for testing.
public class DefaultPlatform extends Platform {
  // System property names for properties expected to define "getters" for platform attributes.
  private static final String BACKEND_FACTORY = "flogger.backend_factory";
  private static final String LOGGING_CONTEXT = "flogger.logging_context";
  private static final String CLOCK = "flogger.clock";

  private final BackendFactory backendFactory;
  private final ContextDataProvider context;
  private final Clock clock;
  private final LogCallerFinder callerFinder;

  public DefaultPlatform() {
    BackendFactory factory = callGetterFromSystemProperty(BACKEND_FACTORY, BackendFactory.class);
    this.backendFactory = (factory != null) ? factory : SimpleBackendFactory.getInstance();
    ContextDataProvider context =
        callGetterFromSystemProperty(LOGGING_CONTEXT, ContextDataProvider.class);
    this.context = (context != null) ? context : ContextDataProvider.getNoOpProvider();
    Clock clock = callGetterFromSystemProperty(CLOCK, Clock.class);
    this.clock = (clock != null) ? clock : SystemClock.getInstance();
    // TODO(dbeaumont): Figure out how to handle StackWalker when it becomes available (Java9).
    this.callerFinder = StackBasedCallerFinder.getInstance();
  }

  // Visible for testing
  DefaultPlatform(
      BackendFactory factory,
      ContextDataProvider context,
      Clock clock,
      LogCallerFinder callerFinder) {
    this.backendFactory = factory;
    this.context = context;
    this.clock = clock;
    this.callerFinder = callerFinder;
  }

  @Override
  protected LogCallerFinder getCallerFinderImpl() {
    return callerFinder;
  }

  @Override
  protected LoggerBackend getBackendImpl(String className) {
    return backendFactory.create(className);
  }

  @Override
  protected ContextDataProvider getContextDataProviderImpl() {
    return context;
  }

  @Override
  protected long getCurrentTimeNanosImpl() {
    return clock.getCurrentTimeNanos();
  }

  @Override
  protected String getConfigInfoImpl() {
    return "Platform: " + getClass().getName() + "\n"
        + "BackendFactory: " + backendFactory + "\n"
        + "Clock: " + clock + "\n"
        + "LoggingContext: " + context + "\n"
        + "LogCallerFinder: " + callerFinder + "\n";
  }
}
