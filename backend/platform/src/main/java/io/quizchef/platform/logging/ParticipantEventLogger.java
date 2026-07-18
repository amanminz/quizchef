package io.quizchef.platform.logging;

import io.quizchef.session.domain.event.AnswerSubmittedEvent;
import io.quizchef.session.domain.event.ParticipantDisconnectedEvent;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs a participant's own lifecycle: joined, reconnected, disconnected,
 * submitted an answer. {@link AnswerSubmittedEvent} carries no
 * correctness/score by domain design (ADR-006), so there is nothing
 * sensitive to withhold here.
 */
@Component
public class ParticipantEventLogger {

    private static final Logger log = LoggerFactory.getLogger(ParticipantEventLogger.class);

    @EventListener
    void on(ParticipantJoinedEvent event) {
        log.info("participant.joined sessionId={} participantId={}",
                event.sessionId(), event.participantId());
    }

    @EventListener
    void on(ParticipantReconnectedEvent event) {
        log.info("participant.reconnected sessionId={} participantId={}",
                event.sessionId(), event.participantId());
    }

    @EventListener
    void on(ParticipantDisconnectedEvent event) {
        log.info("participant.disconnected sessionId={} participantId={}",
                event.sessionId(), event.participantId());
    }

    @EventListener
    void on(AnswerSubmittedEvent event) {
        log.info("participant.answer_submitted sessionId={} participantId={} questionId={}",
                event.sessionId(), event.participantId(), event.questionId());
    }
}
