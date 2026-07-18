package io.quizchef.platform.security.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Same technique as {@code io.quizchef.platform.logging.LogCapture} (PR #2)
 * — duplicated rather than shared across packages for a 20-line test
 * utility.
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
