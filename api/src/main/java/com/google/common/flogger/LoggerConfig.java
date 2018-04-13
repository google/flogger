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

package com.google.common.flogger;

import static com.google.common.flogger.util.Checks.checkArgument;
import static com.google.common.flogger.util.Checks.checkNotNull;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * An adapter for the configuration specific aspects of a JDK logger which retains a strong
 * reference to the corresponding underlying logger to prevent accidental garbage collection.
 * This class is needed to help avoid bugs caused by the premature garbage collection of logger
 * instances (which are only weakly referenced when returned by {@link Logger#getLogger}).
 * <p>
 * All methods in this API simply delegate to the equivalent {@link Logger} method without
 * any additional checking.
 * <p>
 * A small number of small differences exist between using this class and using a Logger
 * instance directly, but these are deliberate and seek to avoid misuse.
 * <ul>
 * <li>The {@code LoggerConfig} API has no "setParent()" method (this should never be called
 *     by application code anyway).
 * <li>It is not possible to obtain a {@code LoggerConfig} instance for an anonymous logger
 *     (the {@code LoggerConfig} API hides the underlying logger, so it would be impossible
 *     to do any logging with such an object). If you are using an anonymous logger then you
 *     must continue to configure it via the {@link Logger} API itself.
 * <li>This API adds the {@literal @}Nullable annotation to parameters and return values where
 *     appropriate.
 * </ul>
 */
@CheckReturnValue
public final class LoggerConfig {
  /**
   * Unbounded strong reference cache of all loggers used for configuration purposes.
   * <p>
   * The number of loggers on which configuration occurs should be small and effectively bounded
   * in all expected use cases, so it should be okay to retain all of them for the life of a task.
   */
  // TODO(user): Reassess the risk of memory leaks here and decide what to do about it.
  private static final Map<String, LoggerConfig> strongRefMap =
      new ConcurrentHashMap<String, LoggerConfig>();

  /** Delegate logger. */
  private final Logger logger;

  /**
   * Returns a configuration instance suitable for the given (non anonymous) fluent logger.
   * <p>
   * This method obtains and wraps the underlying logger associated with the given logger,
   * retaining a strong reference and making it safe to use for persistent configuration changes.
   *
   * @param fluentLogger the name of the logger to be configured via this API.
   */
  public static LoggerConfig of(AbstractLogger<?> fluentLogger) {
    // TODO(user): Revisit if/when Flogger supports anonymous loggers.
    checkArgument(fluentLogger.getName() != null,
        "cannot obtain configuration for an anonymous logger");
    return getConfig(fluentLogger.getName());
  }

/**
   * Returns a configuration instance suitable for configuring the logger of the specified class.
   * <p>
   * This method obtains and wraps the underlying logger for the given class, retaining a strong
   * reference and making it safe to use for persistent configuration changes.
   *
   * @param clazz the class whose logger is to be configured via this API.
   */
  public static LoggerConfig getConfig(Class<?> clazz) {
    return getConfig(clazz.getName());
  }

  /**
   * Returns a configuration instance suitable for configuring loggers in the same package as the
   * specified class.
   * <p>
   * This method obtains and wraps the underlying logger for the given package, retaining a strong
   * reference and making it safe to use for persistent configuration changes.
   *
   * @param clazz a class defining the package for which logger configuration should occur.
   */
  public static LoggerConfig getPackageConfig(Class<?> clazz) {
    return getConfig(clazz.getPackage().getName());
  }

  /**
   * Returns a configuration instance suitable for configuring a logger with the same name.
   * <p>
   * This method obtains and wraps the underlying logger with the given name, retaining a strong
   * reference and making it safe to use for persistent configuration changes. Note that as it makes
   * very little sense to have a logger configuration object which wraps (and hides) an anonymous
   * logger instance, {@code null} names are disallowed.
   *
   * @param name the name of the logger to be configured via this API.
   */
  public static LoggerConfig getConfig(String name) {
    LoggerConfig config = strongRefMap.get(checkNotNull(name, "logger name"));
    if (config == null) {
      // Ignore race condition of multiple put as all instances are equivalent.
      // TODO(user): Add a check and warn if the map grows "too large".
      config = new LoggerConfig(name);
      strongRefMap.put(name, config);
    }
    return config;
  }

  private LoggerConfig(String name) {
    this.logger = checkNotNull(Logger.getLogger(name), "logger");
  }

  /** See {@link Logger#getResourceBundle()}. */
  @Nullable
  public ResourceBundle getResourceBundle() {
    return logger.getResourceBundle();
  }

  /** See {@link Logger#getResourceBundleName()}. */
  @Nullable
  public String getResourceBundleName() {
    return logger.getResourceBundleName();
  }

  /** See {@link Logger#setFilter(Filter)}. */
  public void setFilter(@Nullable Filter newFilter) throws SecurityException {
    logger.setFilter(newFilter);
  }

  /** See {@link Logger#getFilter()}. */
  @Nullable
  public Filter getFilter() {
    return logger.getFilter();
  }

  /** See {@link Logger#setLevel(Level)}. */
  public void setLevel(@Nullable Level newLevel) throws SecurityException {
    logger.setLevel(newLevel);
  }

  /** See {@link Logger#getLevel()}. */
  @Nullable
  public Level getLevel() {
    return logger.getLevel();
  }

  /** See {@link Logger#getName()}. */
  public String getName() {
    return logger.getName();
  }

  /** See {@link Logger#addHandler(Handler)}. */
  public void addHandler(Handler handler) throws SecurityException {
    checkNotNull(handler, "handler");
    logger.addHandler(handler);
  }

  /** See {@link Logger#removeHandler(Handler)}. */
  public void removeHandler(Handler handler) throws SecurityException {
    checkNotNull(handler, "handler");
    logger.removeHandler(handler);
  }

  /** See {@link Logger#getHandlers()}. */
  public Handler[] getHandlers() {
    return logger.getHandlers();
  }

  /** See {@link Logger#setUseParentHandlers(boolean)}. */
  public void setUseParentHandlers(boolean useParentHandlers) {
    logger.setUseParentHandlers(useParentHandlers);
  }

  /** See {@link Logger#getUseParentHandlers()}. */
  public boolean getUseParentHandlers() {
    return logger.getUseParentHandlers();
  }

  /** See {@link Logger#getParent()}. */
  @Nullable
  public Logger getParent() {
    return logger.getParent();
  }
}
