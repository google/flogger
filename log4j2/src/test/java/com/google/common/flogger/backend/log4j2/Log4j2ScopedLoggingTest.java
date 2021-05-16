package com.google.common.flogger.backend.log4j2;

import com.google.common.flogger.GoogleLogContext;
import com.google.common.flogger.GoogleLogger;
import com.google.common.flogger.LogContext;
import com.google.common.flogger.MetadataKey;
import com.google.common.flogger.backend.LoggerBackend;
import com.google.common.flogger.context.ContextDataProvider;
import com.google.common.flogger.context.ScopedLoggingContext;
import com.google.common.flogger.context.Tags;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;
import static org.apache.logging.log4j.Level.INFO;
import static org.apache.logging.log4j.Level.TRACE;

@RunWith(JUnit4.class)
public class Log4j2ScopedLoggingTest {

    private static final AtomicInteger uid = new AtomicInteger();
    private static final MetadataKey<Integer> COUNT_KEY = MetadataKey.single("count", Integer.class);
    private static final MetadataKey<Integer> REP_KEY = MetadataKey.repeated("rep", Integer.class);
    private static final MetadataKey<String> ID_KEY = MetadataKey.single("id", String.class);
    private static final MetadataKey<String> ID_KEY2 = MetadataKey.single("id", String.class);
    private static final MetadataKey<String> TAGS = MetadataKey.single("tags", String.class);
    private static final MetadataKey<String> TAGS2 = MetadataKey.single("tags", String.class);
    private static final MetadataKey<String> REP_TAGS = MetadataKey.repeated("tags", String.class);
    private static GoogleLogger googleLogger;
    private Logger logger;
    private CapturingAppender appender;
    private LoggerBackend backend;
    private List<LogEvent> events;

    @BeforeClass
    public static void init() {
        Configurator.setRootLevel(Level.TRACE);
        System.getProperties().put("flogger.backend_factory", "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance");
        System.getProperties().put("flogger.logging_context", "com.google.common.flogger.grpc.GrpcContextDataProvider#getInstance");
        googleLogger = GoogleLogger.forEnclosingClass();
    }

    @Before
    public void setUpLoggerBackend() {
        // A unique name should produce a different logger for each test allowing tests to be run in
        // parallel.
        String loggerName = String.format("%s_%02d", Log4j2ScopedLoggingTest.class.getName(), uid.incrementAndGet());

        logger = (Logger) LogManager.getLogger(loggerName);
        appender = new CapturingAppender();
        logger.addAppender(appender);
        logger.setLevel(TRACE);
        backend = new Log4j2LoggerBackend(logger);
        events = appender.events;
    }

    @After
    public void tearDown() {
        logger.removeAppender(appender);
        appender.stop();
    }

    void assertLogEntry(int index, Level level, String message, Map<String, Object> contextData) {
        final LogEvent event = events.get(index);
        assertThat(event.getLevel()).isEqualTo(level);
        assertThat(event.getMessage().getFormattedMessage()).isEqualTo(message);
        assertThat(event.getThrown()).isNull();

        for (Map.Entry<String, Object> entry : contextData.entrySet()) {
            assertThat(event.getContextData().containsKey(entry.getKey())).isTrue();
            assertThat(event.getContextData().getValue(entry.getKey()).toString().equals(entry.getValue().toString())).isTrue();
        }
    }

    void assertLogCount(int count) {
        assertThat(events).hasSize(count);
    }

    @Test
    public void testTag() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withTags(Tags.builder().addTag("foo", "bar").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[foo=bar]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testTags() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withTags(Tags.builder().addTag("foo").addTag("bar", "baz").addTag("bar", "baz2").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[bar=baz, bar=baz2, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testSingleCustomTagAsMetadataKey() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(MetadataKey.single("tags", Tags.class), Tags.of("foo", "bar"))
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[foo=bar]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testSingleTagAsMetadataKey() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(LogContext.Key.TAGS, Tags.of("foo", "bar"))
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[foo=bar]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testEmptyCustomTags_doNotDisplay() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(MetadataKey.single("tags", Tags.class), Tags.builder().build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testEmptyTags_doNotDisplay() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(LogContext.Key.TAGS, Tags.builder().build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testClashOfTagsWithSingleNonRepeatableMetadata() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(TAGS, "aTag")
                .withTags(Tags.builder().addTag("foo").addTag("bar", "baz").addTag("bar", "baz2").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[aTag, bar=baz, bar=baz2, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testClashOfTagsWithMultipleRepeatableMetadata() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(REP_TAGS, "aTag")
                .withMetadata(REP_TAGS, "anotherTag")
                .withTags(Tags.builder().addTag("foo").addTag("bar", "baz").addTag("bar", "baz2").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[aTag, anotherTag, bar=baz, bar=baz2, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testClashOfTagsWithMultipleNonRepeatableMetadata() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(TAGS, "aTag")
                .withMetadata(TAGS, "anotherTag")
                .withTags(Tags.builder().addTag("foo").addTag("bar", "baz").addTag("bar", "baz2").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[anotherTag, bar=baz, bar=baz2, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testClashOfTagsWithMetadataHoldingAList() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(MetadataKey.single("tags", List.class), Arrays.asList(1, 2, 3))
                .withTags(Tags.builder().addTag("foo").addTag("bar", "baz").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[[1, 2, 3], bar=baz, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testClashOfTagsWithMetadataHoldingAListAndRepeatableMetadata() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(MetadataKey.single("tags", List.class), Arrays.asList(1, 2, 3))
                .withMetadata(REP_TAGS, "a")
                .withMetadata(REP_TAGS, "b")
                .withTags(Tags.builder().addTag("foo").addTag("bar", "baz").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[[1, 2, 3], a, b, bar=baz, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testTagsWithRepeatableMetadataClash() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(REP_TAGS, "aValue")
                .withMetadata(REP_TAGS, "anotherValue")
                .withTags(Tags.builder().addTag("foo").addTag("bar", "baz").addTag("bar", "baz2").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("tags", "[aValue, anotherValue, bar=baz, bar=baz2, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testMultipleRepeatableMetadata() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(REP_KEY, 1)
                .withMetadata(REP_KEY, 2)
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("rep", "[1, 2]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testMultipleNonRepeatableMetadataSameMetadataKey() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(ID_KEY, "001")
                .withMetadata(ID_KEY2, "002")
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("id", "[001, 002]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testMultipleNonRepeatableMetadataDifferentMetadataKey() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(COUNT_KEY, 1)
                .withMetadata(COUNT_KEY, 2)
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("count", 2);
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testSingleNonRepeatableMetadata() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(COUNT_KEY, 23)
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("count", 23);
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testSingleNonRepeatableMetadataList() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(MetadataKey.single("items", List.class), Arrays.asList(23))
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("items", Collections.singletonList(23));
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testScopedLoggingContext() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(COUNT_KEY, 23)
                .withTags(Tags.builder().addTag("foo").addTag("baz", "bar").addTag("baz", "bar2").build())
                .install()
        ) {
            GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
            logContext.log("test");
            backend.log(logContext); // this event will be caught
            Map<String, Object> contextMap = new HashMap<String, Object>();
            contextMap.put("count", 23);
            contextMap.put("tags", "[baz=bar, baz=bar2, foo]");
            assertLogCount(1);
            assertLogEntry(0, INFO, "test", contextMap);
        }
    }

    @Test
    public void testNestedScopedLoggingContext() {
        try (ScopedLoggingContext.LoggingContextCloseable ctx = ContextDataProvider.getInstance()
                .getContextApiSingleton()
                .newContext()
                .withMetadata(ID_KEY, "001")
                .withTags(Tags.builder().addTag("foo").addTag("baz", "bar").build())
                .install()
        ) {
            try (ScopedLoggingContext.LoggingContextCloseable ctx2 = ContextDataProvider.getInstance()
                    .getContextApiSingleton()
                    .newContext()
                    .withMetadata(ID_KEY, "002")
                    .withTags(Tags.builder().addTag("foo").addTag("baz", "bar2").build())
                    .install()
            ) {
                GoogleLogContext logContext = (GoogleLogContext) googleLogger.atInfo();
                logContext.log("test");
                backend.log(logContext); // this event will be caught
                Map<String, Object> contextMap = new HashMap<String, Object>();
                contextMap.put("id", "002");
                contextMap.put("tags", "[baz=bar, baz=bar2, foo]");
                assertLogCount(1);
                assertLogEntry(0, INFO, "test", contextMap);
            }
        }
    }

    private static final class CapturingAppender extends AbstractAppender {
        static final String NAME = "Capturing Appender";
        private final List<LogEvent> events = new ArrayList<>();

        CapturingAppender() {
            super(NAME, null, PatternLayout.createDefaultLayout(), true, null);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event);
        }
    }
}
