package io.quizchef.platform.logging;

import io.quizchef.session.domain.event.LobbyOpenedEvent;
import io.quizchef.session.domain.event.SessionCreatedEvent;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.event.SessionStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs a session's own lifecycle: create, lobby opened, game started,
 * session finished. Question-level gameplay transitions live in
 * {@link GameplayEventLogger}.
 */
@Component
public class SessionEventLogger {

    private static final Logger log = LoggerFactory.getLogger(SessionEventLogger.class);

    @EventListener
    void on(SessionCreatedEvent event) {
        log.info("session.created sessionId={} hostId={} publishedQuizVersionId={}",
                event.sessionId(), event.host().identityId(), event.publishedQuizVersionId());
    }

    @EventListener
    void on(LobbyOpenedEvent event) {
        log.info("session.lobby_opened sessionId={}", event.sessionId());
    }

    @EventListener
    void on(SessionStartedEvent event) {
        log.info("session.started sessionId={}", event.sessionId());
    }

    @EventListener
    void on(SessionFinishedEvent event) {
        log.info("session.finished sessionId={}", event.sessionId());
    }
}
