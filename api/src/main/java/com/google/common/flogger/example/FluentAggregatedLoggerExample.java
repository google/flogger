package com.google.common.flogger.example;

import com.google.common.flogger.EventAggregator;
import com.google.common.flogger.FluentAggregatedLogger;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StatAggregator;

import java.util.logging.Level;

/**
 * @Desctiption
 * @Author wallace
 * @Date 2020/12/26
 */
public class FluentAggregatedLoggerExample {
	final static FluentLogger logger1 = FluentLogger.forEnclosingClass();
	final static FluentAggregatedLogger logger2 = FluentAggregatedLogger.forEnclosingClass();


	public static void main(String[] args) throws InterruptedException {
		logger1.at(Level.INFO).log("hello, world");

		EventAggregator eventAggregator = logger2.getEvent("get-user-api-resp").timeWindow(5).numberWindow(20);
		for(int i = 0; i < 92; i++) {
			eventAggregator.add("requestId=10" + i, "200");
		}

		StatAggregator statAggregator = logger2.getStat("get-user-api-perf").timeWindow(5).numberWindow(10);
		for(int i = 0; i < 100; i++) {
			statAggregator.add(i);
		}

		Thread.sleep(3000);

		statAggregator.add(100);
	}
}
