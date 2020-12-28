package com.google.common.flogger;

import com.google.common.flogger.backend.LoggerBackend;

/**
 * @Desctiption
 * @Author wallace
 * @Date 2020/12/26
 */
public abstract class AbstractAggregatedLogger extends AbstractLogger {

	protected AbstractAggregatedLogger(LoggerBackend backend) {
		super(backend);
	}
}
