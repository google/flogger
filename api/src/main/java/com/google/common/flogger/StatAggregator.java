package com.google.common.flogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @Desctiption
 * @Author wallace
 * @Date 2020/12/28
 */
public class StatAggregator extends AggregatedLogContext<FluentAggregatedLogger, StatAggregator> {

	protected final BlockingQueue<Long> valueList =
			new LinkedBlockingQueue<Long>(1024 * 1024);

	protected StatAggregator(String name, FluentAggregatedLogger logger, LogSite logSite, ScheduledExecutorService pool) {
		super(name, logger, logSite, pool);
	}

	@Override
	protected StatAggregator self() {
		return this;
	}

	public void add(long value){
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
		valueList.drainTo(valueBuffer, getMetadata().findValue(Key.NUMBER_WINDOW));

		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		long total = 0;
		long avg = 0;

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
			avg = total / valueBuffer.size();

			String sep = ", ";
			builder.append("min:").append(min).append(sep)
					.append("max:").append(max).append(sep)
					.append("total:").append(total).append(sep)
					.append("count:").append(valueBuffer.size()).append(sep)
					.append("avg:").append(avg).append(".");
		} else {
			builder.append("no data\n");
		}

		return builder.toString();
	}
}
