/*
 * Copyright (C) 2020 The Flogger Authors.
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

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.TemplateContext;
import com.google.common.flogger.parser.DefaultPrintfMessageParser;
import com.google.common.flogger.util.Checks;
import com.google.errorprone.annotations.CheckReturnValue;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.flogger.util.Checks.checkNotNull;

/**
 * Base class for the aggregated logger API.
 *
 * @param <LOGGER> the {@link AbstractLogger} to write log data
 * @param <API>    the {@link AggregatedLoggingApi} to format log data
 */
@CheckReturnValue
public abstract class AggregatedLogContext<LOGGER extends AbstractLogger,
		API extends AggregatedLoggingApi> implements AggregatedLoggingApi {

	/**
	 * Available configuration Key
	 */
	public static final class Key {
		private Key() {}

		/**
		 * Time window for aggregating log.
		 * {@link AbstractLogger} will log all data at the end of the time window.
		 */
		public static final MetadataKey<Integer> TIME_WINDOW =
				MetadataKey.single("time_window", Integer.class);

		/**
		 * Number window for aggregating log.
		 * {@link AbstractLogger} will log data when the number of data is up to number window.
		 */
		public static final MetadataKey<Integer> NUMBER_WINDOW =
				MetadataKey.single("number_window", Integer.class);
	}

	private final class LogFlusher implements Runnable {
		@Override
		public void run() {
			flush(0); // Always flush all data

			// Write one more log to show timer is running when there is no any data.
			LogData logData = getLogData(getName() + " periodically flush log finished");
			getLogger().write(logData);
		}
	}

	//Runnable for time window
	private volatile LogFlusher flusher;

	//Only one thread will flush log
	private AtomicBoolean flushLock = new AtomicBoolean(false);

	protected final MutableMetadata metadata = new MutableMetadata();

	protected final String name;
	protected final FluentAggregatedLogger logger;
	protected final LogSite logSite;
	protected final ScheduledExecutorService pool;

	protected abstract API self();

	/**
	 * Instantiates a new AggregatedLogContext.
	 *
	 * @param name    the name
	 * @param logger  the logger (see {@link FluentAggregatedLogger}).
	 * @param logSite the log site (see {@link LogSite}).
	 * @param pool    the executor service pool used to periodically log data.
	 */
	protected AggregatedLogContext(String name, FluentAggregatedLogger logger, LogSite logSite, ScheduledExecutorService pool){
		this.name = checkNotNull(name, "name");
		this.logger = checkNotNull(logger, "logger");
		this.logSite = checkNotNull(logSite, "logSite");
		this.pool = checkNotNull(pool, "pool");
	}

	public String getName() {
		return name;
	}

	/**
	 * Schedule log flusher at fixed rate of time window.
	 * Please call withTimeWindow() before start() to set time window.
	 *
	 */
	 public synchronized API start(){
	 	if(flusher != null){
	 		return self();
	 	}

	 	int period = getTimeWindow();
		flusher = new LogFlusher();
		pool.scheduleAtFixedRate(flusher, 2, period, TimeUnit.SECONDS);

		return self();
	}

	@Override
	public API withTimeWindow(int seconds) {
		if(flusher != null){
	 		throw new RuntimeException("Please do not change time window after logger start.");
	    }

		Checks.checkArgument(seconds > 0 && seconds <= 3600,
				"Time window range should be (0,3600]");

		metadata.addValue(Key.TIME_WINDOW, seconds);

		return self();
	}

	@Override
	public API withNumberWindow(int number) {
		Checks.checkArgument(number > 0 && number <= 1000 * 1000,
				"Number window range should be (0, 1000000])");

		metadata.addValue(Key.NUMBER_WINDOW, number);
		return self();
	}

	/**
	 * Get time window configuration. Default value is 60(seconds).
	 *
	 */
	@Override
	public int getTimeWindow() {
		Integer timeWindow = metadata.findValue(Key.TIME_WINDOW);
		return timeWindow == null ? 60 : timeWindow; // Default 60 seconds
	}

	/**
	 * Get number window configuration. Default value is 100.
	 *
	 */
	@Override
	public int getNumberWindow() {
		Integer numberWindow = metadata.findValue(Key.NUMBER_WINDOW);
		return numberWindow == null ? 100 : numberWindow;  // Default 100
	}

	/**
	 * Gets metadata.
	 *
	 * @return the metadata
	 */
	protected Metadata getMetadata() {
		return metadata != null ? metadata : Metadata.empty();

	}

	/**
	 * Gets logger.
	 *
	 * @return the logger
	 */
	protected FluentAggregatedLogger getLogger() {
		return logger;
	}

	/**
	 * Generate {@link LogData} for logger backend.
	 *
	 * @return the log data
	 */
	protected LogData getLogData(String message) {
		long timestampNanos = Platform.getCurrentTimeNanos();
		String loggerName = getLogger().getBackend().getLoggerName();

		DefaultLogData logData = new DefaultLogData(timestampNanos, loggerName);
		logData.setMetadata(getMetadata());

		logData.setLogSite(logSite);
		logData.setTemplateContext(new TemplateContext(DefaultPrintfMessageParser.getInstance(), message));

		//use empty array for avoiding null exception
		logData.setArgs(new Object[]{});

		return logData;
	}

	/**
	 * Flush aggregated data in new Thread.
	 */
	protected void asyncFlush(final int count){
		new Thread(new Runnable() {
			@Override
			public void run() {
				flush(count);
			}
		}).start();
	}

	/**
	 * 	Visible for test.Because real logging action is done in different thread,
	 * 	junit will not get the async logging data when running unit test.
	 * 	So call log method directly in junit testcase.
	 */
	protected void flush(int count){
		if(flushLock.compareAndSet(false,true)) {
			try {
				if(count > 0 && haveData() < count){
					return;
				}

				LogData logData = getLogData(message(count));
				getLogger().write(logData);
			}
			finally {
				flushLock.compareAndSet(true, false);
			}
		}
	}
}
