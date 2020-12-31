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

package com.google.common.flogger.example;

import com.google.common.flogger.EventAggregator;
import com.google.common.flogger.FluentAggregatedLogger;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StatAggregator;

import java.util.logging.Level;

/**
 * FluentAggregatedLogger Example.
 * You can run this example directly from Intellij IDE by choose "fluent_aggregated_logger_example" target.
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

		StatAggregator statAggregator = logger2.getStat("get-user-api-perf")
				.numberWindow(10).setSampleRate(3).setUnit("ms");
		for(int i = 0; i < 100; i++) {
			statAggregator.add(i);
		}
	}
}
