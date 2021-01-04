/*
 * Copyright (C) 2012 The Flogger Authors.
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

import static com.google.common.flogger.util.Checks.*;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.TemplateContext;
import com.google.common.flogger.parser.DefaultPrintfMessageParser;
import com.google.common.flogger.util.Checks;
import com.google.errorprone.annotations.CheckReturnValue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
			log();
		}
	}

	//Counter for number window
	private AtomicLong counter = new AtomicLong(0);

	//Runnable for time window
	private LogFlusher flusher;

	//Only one thread will flush log
	private AtomicBoolean flushLock = new AtomicBoolean(false);

	protected final MutableMetadata metadata = new MutableMetadata();

	protected final String name;
	protected final FluentAggregatedLogger logger;
	protected final LogSite logSite;
	protected final ScheduledExecutorService pool;

	protected abstract API self();

	/**
	 * Check if there are some data to log.
	 *
	 * @return the boolean
	 */
	protected abstract boolean haveData();

	/**
	 * Format aggregated data to string for logging.
	 *
	 * @return the string
	 */
	protected abstract String message();

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

	/**
	 * Schedule log flusher at fixed rate
	 *
	 * @param period
	 */
	private void start(long period){
		flusher = new LogFlusher();
		pool.scheduleAtFixedRate(flusher, 2, period, TimeUnit.SECONDS);
	}

	@Override
	public synchronized API timeWindow(int seconds) {
		if(flusher != null){
			return self();
		}

		Checks.checkArgument(seconds > 0, "Time window should be larger than 0");

		metadata.addValue(Key.TIME_WINDOW, seconds); //just for logger backend to print CONTEXT
		start(seconds);

		return self();
	}

	@Override
	public API numberWindow(int number) {
		Checks.checkArgument(number > 0, "Number window should be larger than 0");

		metadata.addValue(Key.NUMBER_WINDOW, number);
		return self();
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
	 * Increase counter.
	 */
	protected void increaseCounter(){
		increaseCounter(0);
	}

	/**
	 * Increase counter.
	 *
	 * @param delta the delta
	 */
	protected void increaseCounter(int delta){
		counter.addAndGet(delta);
	}

	/**
	 * Check if it's time to flush data. Only for number window.
	 * No need to check for time window because executor service pool will periodically flush data.
	 *
	 * @return the boolean
	 */
	protected boolean shouldFlush() {
		//check number window
		Long currentCounter = counter.get();
		int numberWindows = metadata.findValue(Key.NUMBER_WINDOW);

		//No need to check if currentCounter < 0
		if(currentCounter % numberWindows == 0){
			return true;
		}

		return false;
	}

	/**
	 * Generate {@link LogData} for logger backend.
	 *
	 * @return the log data
	 */
	protected LogData data() {
		long timestampNanos = Platform.getCurrentTimeNanos();
		String loggerName = getLogger().getBackend().getLoggerName();

		DefaultLogData logData = new DefaultLogData(timestampNanos, loggerName);
		logData.setMetadata(getMetadata());

		logData.setLogSite(logSite);

		String message = message();
		logData.setTemplateContext(new TemplateContext(DefaultPrintfMessageParser.getInstance(), message));

		//use empty array for avoiding null exception
		logData.setArgs(new Object[]{});

		return logData;
	}

	/**
	 * Flush aggregated data in new Thread.
	 */
	protected void flush(){
		new Thread(new Runnable() {
			@Override
			public void run() {
				log();
			}
		}).start();
	}

	private void log(){
		if(flushLock.compareAndSet(false,true)) {
			try {
				do {
					boolean have = haveData();

					//even if there is no data to log, we still write a empty log to show timer is working.
					LogData logData = data();
					getLogger().write(logData);

					if(!have){
						break;
					}
				}while (true);
			}
			finally {
				flushLock.compareAndSet(true, false);
			}
		}
	}
}
