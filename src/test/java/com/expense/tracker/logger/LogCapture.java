package com.expense.tracker.logger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Captura en memoria los eventos de logback de una clase, para asertar las líneas que emite.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> type) {
        this.logger = (Logger) LoggerFactory.getLogger(type);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture(type);
    }

    public List<ILoggingEvent> events() {
        return appender.list;
    }

    public List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
