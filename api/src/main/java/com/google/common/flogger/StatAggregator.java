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

import com.google.common.flogger.util.Checks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StatAggregator is used to log many same type performance data. The typical scenario is API process.
 * <p>
 * StatAggregator can aggregate many same type performance data and simply calc the min/max/total/avg value.
 * For example:
 *  API: get-user-api
 *  min:60, max:87, total:735, count:10, avg:73.5.
 *  [CONTEXT number_window=10 sample_rate=3 unit="ms" ]
 * <p>
 * You can combine {@link EventAggregator} and {@link StatAggregator} and {@link FluentLogger}
 * to log full information for API or other kind of event like this:
 *     - use {@link EventAggregator} to log key information like request id and response code.
 *     - use {@link StatAggregator} to log performance information
 *     - use {@link FluentLogger} to log detailed information for error requests or responses.
 *
 */
public class StatAggregator extends AggregatedLogContext<FluentAggregatedLogger, StatAggregator> {

	public static final class Key {
		private Key() {
		}

		public static final MetadataKey<Integer> SAMPLE_RATE =
				MetadataKey.single("sample_rate", Integer.class);

		public static final MetadataKey<String> UNIT_STRING =
				MetadataKey.single("unit", String.class);
	}

	//Only calc some data
	private volatile int sampleRate = 1; //Calc all data by default.
	private final AtomicInteger sampleCounter = new AtomicInteger(1);

	/**
	 * Use LinkedBlockingQueue to store values.
	 * <p>
	 * Two reasons for using LinkedBlockingQueue:
	 * 1. Thread-safe: many threads will use the same {@link StatAggregator} to log same type values.
	 * 2. Async log: logging aggregated value is a time-consuming action. It's better to use separate thread to do it.
	 */
	protected final BlockingQueue<Long> valueList =
			new LinkedBlockingQueue<Long>(1024 * 1024);

	protected StatAggregator(String name, FluentAggregatedLogger logger, LogSite logSite, ScheduledExecutorService pool) {
		super(name, logger, logSite, pool);
	}

	@Override
	protected StatAggregator self() {
		return this;
	}

	/**
	 * Set sample rate
	 *
	 * @param sampleRate
	 * @return
	 */
	public StatAggregator setSampleRate(int sampleRate){
		Checks.checkArgument(sampleRate > 0, "Sample rate should be larger than 0");

		this.sampleRate = sampleRate;
		metadata.addValue(Key.SAMPLE_RATE, sampleRate);

		return self();
	}

	/**
	 * Set unit for log context.
	 *
	 * @param unit
	 * @return
	 */
	public StatAggregator setUnit(String unit){
		metadata.addValue(Key.UNIT_STRING, unit);

		return self();
	}

	/**
	 * Add value
	 *
	 * @param value
	 */
	public void add(long value){
		if(!sample()){
			return;
		}

		valueList.offer(value);
		increaseCounter();

		if(shouldFlush()){
			flush();
		}
	}

	@Override
	protected boolean haveData() {
		return !valueList.isEmpty();
	}

	@Override
	protected String message() {
		List<Long> valueBuffer = new ArrayList<Long>();
		valueList.drainTo(valueBuffer, metadata.findValue(AggregatedLogContext.Key.NUMBER_WINDOW));

		return formatMessage(valueBuffer);
	}

	private boolean sample(){
		return sampleRate == 1 || (sampleCounter.getAndDecrement() % sampleRate == 0);
	}

	private String formatMessage(List<Long> valueBuffer){

		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		long total = 0;
		double avg = 0;

		for(Long e : valueBuffer){
			if(e > max){
				max = e;
			}

			if(e < min){
				min = e;
			}

			total += e;
		}

		StringBuilder builder = new StringBuilder();
		builder.append(name).append("\n");

		if(!valueBuffer.isEmpty()) {
			avg = Double.valueOf(total) / valueBuffer.size();

			String sep = ", ";
			builder.append("min:").append(min).append(sep)
					.append("max:").append(max).append(sep)
					.append("total:").append(total).append(sep)
					.append("count:").append(valueBuffer.size()).append(sep)
					.append("avg:").append(avg).append(".");
		} else {
			builder.append(" ");
		}

		return builder.toString();
	}
}
