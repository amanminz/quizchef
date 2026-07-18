package io.quizchef.platform.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Attaches a Logback {@link ListAppender} to one logger for the duration of
 * a test, so an event logger's output can be asserted without depending on
 * Spring Boot's structured-logging formatting (that is Spring Boot's own
 * tested feature, not ours).
 */
final class LogCapture implements AutoCloseable {

    private final Logger logbackLogger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture(Class<?> loggerClass) {
        logbackLogger = (Logger) LoggerFactory.getLogger(loggerClass);
        appender.start();
        logbackLogger.addAppender(appender);
    }

    List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logbackLogger.detachAppender(appender);
        appender.stop();
    }
}
