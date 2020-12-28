package com.google.common.flogger;

import static com.google.common.flogger.util.Checks.*;

import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.Platform;
import com.google.common.flogger.backend.TemplateContext;
import com.google.common.flogger.parser.DefaultPrintfMessageParser;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Desctiption
 * @Author wallace
 * @Date 2020/12/26
 */
public abstract class AggregatedLogContext<LOGGER extends AbstractAggregatedLogger,
		API extends AggregatedLoggingApi> implements AggregatedLoggingApi {

	public static final class Key {
		private Key() {}
		public static final MetadataKey<Integer> TIME_WINDOW =
				MetadataKey.single("time_window", Integer.class);

		public static final MetadataKey<Integer> NUMBER_WINDOW =
				MetadataKey.single("number_window", Integer.class);
	}

	private final class LogFlusher implements Runnable {
		@Override
		public void run() {
			log();
		}
	}

	private AtomicLong counter = new AtomicLong(0);
	private AtomicBoolean flushLock = new AtomicBoolean(false);
	private LogFlusher flusher;
	private final MutableMetadata metadata = new MutableMetadata();

	protected final String name;
	protected final FluentAggregatedLogger logger;
	protected final LogSite logSite;
	protected final ScheduledExecutorService pool;

	protected abstract API self();
	protected abstract boolean haveData();
	protected abstract String message();

	protected AggregatedLogContext(String name, FluentAggregatedLogger logger, LogSite logSite, ScheduledExecutorService pool){
		this.name = checkNotNull(name, "name");
		this.logger = checkNotNull(logger, "logger");
		this.logSite = checkNotNull(logSite, "logSite");
		this.pool = checkNotNull(pool, "pool");
	}

	private void start(long period){
		flusher = new LogFlusher();
		pool.scheduleAtFixedRate(flusher, 2, period, TimeUnit.SECONDS);
	}

	@Override
	public synchronized API timeWindow(int seconds) {
		if(flusher != null){
			throw new RuntimeException("Do not set time window repeatedly");
		}

		metadata.addValue(Key.TIME_WINDOW, seconds); //just for logger backend to print CONTEXT
		start(seconds);

		return self();
	}

	@Override
	public API numberWindow(int number) {
		metadata.addValue(Key.NUMBER_WINDOW, number);
		return self();
	}

	protected Metadata getMetadata() {
		return metadata != null ? metadata : Metadata.empty();

	}

	protected FluentAggregatedLogger getLogger() {
		return logger;
	}

	protected void increaseCounter(){
		increaseCounter(0);
	}

	protected void increaseCounter(int delta){
		counter.addAndGet(delta);
	}

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
