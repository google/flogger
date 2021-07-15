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

import static com.google.common.flogger.util.StaticMethodCaller.getInstanceFromSystemProperty;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.context.ContextDataProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * The default fluent logger platform for a server-side Java environment.
 *
 * <p>This class allows configuration via a number of service types. A single instance of each
 * service type may be provided, either via the classpath using <i>service providers</i> (see {@link
 * ServiceLoader}) or by system property. For most users, configuring one of these should just
 * require including the appropriate dependency.
 *
 * <p>If set, the system property for each service type takes precedence over any implementations
 * that may be found on the classpath. The value of the system property is expected to be of one of
 * two forms:
 *
 * <ul>
 *   <li><b>A fully-qualified class name:</b> In this case, the platform will attempt to get an
 *       instance of that class by invoking the public no-arg constructor. If the class defines a
 *       public static no-arg {@code getInstance} method, the platform will call that instead.
 *       <b>Note:</b> Support for {@code getInstance} is only provided to facilitate transition
 *       from older service implementations that include a {@code getInstance} method and will
 *       likely be removed in the future.
 *   <li><b>A fully-qualified class name followed by "#" and the name of a static method:</b> In
 *       this case, the platform will attempt to get an instance of that class by invoking either
 *       the named no-arg static method or the public no-arg constructor. <b>Note:</b> This option
 *       exists only for compatibility with previous Flogger behavior and may be removed in the
 *       future; service implementations should prefer providing a no-arg public constructor rather
 *       than a static method and system properties should prefer only including the class name.
 * </ul>
 *
 * <p>The services used by this platform are the following:
 *
 * <table>
 * <tr>
 * <th>Service Type</th>
 * <th>System Property</th>
 * <th>Default</th>
 * </tr>
 * <tr>
 * <td>{@link BackendFactory}</td>
 * <td>{@code flogger.backend_factory}</td>
 * <td>{@link SimpleBackendFactory}, a {@code java.util.logging} backend</td>
 * </tr>
 * <tr>
 * <td>{@link ContextDataProvider}</td>
 * <td>{@code flogger.logging_context}</td>
 * <td>A no-op {@code ContextDataProvider}</td>
 * </tr>
 * <tr>
 * <td>{@link Clock}</td>
 * <td>{@code flogger.clock}</td>
 * <td>{@link SystemClock}, a millisecond-precision clock</td>
 * </tr>
 * </table>
 */
// Non-final for testing.
public class DefaultPlatform extends Platform {
  // System property names for properties expected to define "getters" for platform attributes.
  private static final String BACKEND_FACTORY = "flogger.backend_factory";
  private static final String CONTEXT_DATA_PROVIDER = "flogger.logging_context";
  private static final String CLOCK = "flogger.clock";

  private final BackendFactory backendFactory;
  private final ContextDataProvider context;
  private final Clock clock;
  private final LogCallerFinder callerFinder;

  public DefaultPlatform() {
    // To avoid eagerly loading the default implementations of each service when they might not
    // be required, we return null from the loadService() method rather than accepting a default
    // instance. This avoids a bunch of potentially unnecessary static initialization.
    BackendFactory backendFactory = loadService(BackendFactory.class, BACKEND_FACTORY);
    this.backendFactory =
        backendFactory != null ? backendFactory : SimpleBackendFactory.getInstance();

    ContextDataProvider contextDataProvider =
        loadService(ContextDataProvider.class, CONTEXT_DATA_PROVIDER);
    this.context =
        contextDataProvider != null ? contextDataProvider : ContextDataProvider.getNoOpProvider();

    Clock clock = loadService(Clock.class, CLOCK);
    this.clock = clock != null ? clock : SystemClock.getInstance();

    this.callerFinder = StackBasedCallerFinder.getInstance();
  }

  /**
   * Attempts to load an implementation of the given {@code serviceType}:
   *
   * <ol>
   *   <li>First looks for an implementation specified by the value of the given {@code
   *       systemProperty}, if that system property is set correctly. If the property is set but
   *       can't be used to get an instance of the service type, prints an error and returns {@code
   *       null}.
   *   <li>Then attempts to load an implementation from the classpath via {@code ServiceLoader}, if
   *       there is exactly one. If there is more than one, prints an error and returns {@code
   *       null}.
   *   <li>If neither is present, returns {@code null}.
   * </ol>
   */
  @NullableDecl
  private static <S> S loadService(Class<S> serviceType, String systemProperty) {
    // TODO(cgdecker): Throw an exception if configuration is present but invalid?
    // - If the system property is set but using it to get the service fails.
    // - If the system property is not set and more than one service is loaded by ServiceLoader
    // If no configuration is present, falling back to the default makes sense, but when invalid
    // configuration is present it may be best to attempt to fail fast.
    S service = getInstanceFromSystemProperty(systemProperty, serviceType);
    if (service != null) {
      // Service was loaded successfully via an explicitly overridden system property.
      return service;
    }

    List<S> loadedServices = new ArrayList<S>();
    for (S loaded : ServiceLoader.load(serviceType)) {
      loadedServices.add(loaded);
    }

    switch (loadedServices.size()) {
      case 0:
        // Normal use of default service when nothing else exists.
        return null;
      case 1:
        // A single service implementation was found and loaded automatically.
        return loadedServices.get(0);
      default:
        System.err.printf(
            "Multiple implementations of service %s found on the classpath: %s%n"
                + "Ensure only the service implementation you want to use is included on the "
                + "classpath or else specify the service class at startup with the '%s' system "
                + "property. The default implementation will be used instead.%n",
            serviceType.getName(), loadedServices, systemProperty);
        return null;
    }
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
        + "ContextDataProvider: " + context + "\n"
        + "LogCallerFinder: " + callerFinder + "\n";
  }
}
