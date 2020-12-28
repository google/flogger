package com.google.common.flogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @Desctiption
 * @Author wallace
 * @Date 2020/12/26
 */
public class EventAggregator extends AggregatedLogContext<FluentAggregatedLogger, EventAggregator> {

	static final class EventPair {
		private final String key;
		private final String value;

		public EventPair(String key, String value){
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}
	}

	protected final BlockingQueue<EventAggregator.EventPair> eventList =
			new LinkedBlockingQueue<EventAggregator.EventPair>(1024 * 1024);

	public EventAggregator(String name, FluentAggregatedLogger logger, LogSite logSite, ScheduledExecutorService pool){
		super(name, logger, logSite, pool);
	}

	@Override
	protected EventAggregator self() {
		return this;
	}

	public void add(String event, String content){
		eventList.offer(new EventPair(event, content));
		increaseCounter();

		if(shouldFlush()){
			flush();
		}
	}

	@Override
	protected boolean haveData() {
		return !eventList.isEmpty();
	}

	@Override
	protected String message(){
		List<EventPair> eventBuffer = new ArrayList<EventPair>();
		eventList.drainTo(eventBuffer, getMetadata().findValue(Key.NUMBER_WINDOW));

		int bufferSize = eventBuffer.size();
		StringBuilder builder = new StringBuilder();
		builder.append(name).append("\n");
		for( int i = 0; i < bufferSize; i++){
			builder.append(eventBuffer.get(i).getKey()).append(":")
					.append(eventBuffer.get(i).getValue()).append(" | ");
			if((i+1) % 10 == 0){
				builder.append("\n");
			}
		}
		if(bufferSize % 10 != 0){
			builder.append("\n");
		}

		builder.append("\nthread:").append(Thread.currentThread().getId());
		builder.append(", total: ").append(bufferSize);

		return builder.toString();
	}
}
