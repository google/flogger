package com.google.common.flogger.backend.log4j2;

import com.google.common.flogger.backend.BaseMessageFormatter;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.LogMessageFormatter;
import com.google.common.flogger.backend.MetadataProcessor;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.logging.Level;

public class Log4j2LogMessageFormatter extends LogMessageFormatter {

    @Override
    public StringBuilder append(LogData logData, MetadataProcessor metadata, StringBuilder buffer) {
        return BaseMessageFormatter.appendFormattedMessage(logData, buffer);
    }

    /**
     * Callback made when formatting of a log message is complete. This is preferable to having the
     * formatter itself hold state because in some common situations an instance of the formatter need
     * not even be allocated.
     */
    public interface SimpleLogHandler {
        /**
         * Handles a single formatted log statement with the given level, message and "cause". This is
         * called back exactly once, from the same thread, for every call made to {@link #format}.
         */
        void handleFormattedLogMessage(Level level, String message, @NullableDecl Throwable thrown);
    }
}
