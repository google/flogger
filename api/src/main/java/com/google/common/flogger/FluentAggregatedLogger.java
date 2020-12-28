package com.google.common.flogger;

import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.backend.Platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.flogger.util.Checks.checkNotNull;

/**
 * @Desctiption
 * @Author wallace
 * @Date 2020/12/26
 */
public final class FluentAggregatedLogger extends AbstractAggregatedLogger {
	private static final ScheduledExecutorService pool = Executors.newScheduledThreadPool(8);

	private final Map<String, AggregatedLogContext> aggregatorMap = new ConcurrentHashMap<String, AggregatedLogContext>();

	private FluentAggregatedLogger(LoggerBackend backend) {
		super(backend);
	}

	public static FluentAggregatedLogger forEnclosingClass() {
		// NOTE: It is _vital_ that the call to "caller finder" is made directly inside the static
		// factory method. See getCallerFinder() for more information.
		String loggingClass = Platform.getCallerFinder().findLoggingClass(FluentAggregatedLogger.class);
		return new FluentAggregatedLogger(Platform.getBackend(loggingClass));
	}

	public EventAggregator getEvent(String name ){
		AggregatedLogContext aggregator = aggregatorMap.get(name);
		if(aggregator == null){
			//Get logsite here for async write data in new Thread
			LogSite logSite = checkNotNull(Platform.getCallerFinder().findLogSite(FluentAggregatedLogger.class, 0),
					"logger backend must not return a null LogSite");
			aggregator = new EventAggregator(name, this, logSite, pool);
			aggregatorMap.putIfAbsent(name, aggregator);
			aggregator = aggregatorMap.get(name);
		}

		if( !(aggregator instanceof EventAggregator)){
			throw new RuntimeException("There is another kind of logger with the same name");
		}

		return (EventAggregator)aggregator;
	}

	public StatAggregator getStat(String name ){
		AggregatedLogContext aggregator = aggregatorMap.get(name);
		if(aggregator == null){
			//Get logsite here for async write data in new Thread
			LogSite logSite = checkNotNull(Platform.getCallerFinder().findLogSite(FluentAggregatedLogger.class, 0),
					"logger backend must not return a null LogSite");
			aggregator = new StatAggregator(name, this, logSite, pool);
			aggregatorMap.putIfAbsent(name, aggregator);
			aggregator = aggregatorMap.get(name);
		}

		if( !(aggregator instanceof StatAggregator)){
			throw new RuntimeException("There is another kind of logger with the same name: " + name);
		}

		return (StatAggregator)aggregator;
	}
}
