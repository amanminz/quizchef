package io.quizchef.platform.logging;

import io.quizchef.session.domain.event.AnswerRevealedEvent;
import io.quizchef.session.domain.event.LeaderboardUpdatedEvent;
import io.quizchef.session.domain.event.QuestionClosedEvent;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs the in-progress question phase machine: opened, closed, answer
 * revealed, leaderboard shown.
 *
 * <p>{@link AnswerRevealedEvent} logs correct option ids — safe, because by
 * definition the reveal is the moment correctness stops being sensitive
 * (ADR-006). {@link LeaderboardUpdatedEvent} logs only the standings size,
 * never every participant's score — this is an operational event, not a
 * replacement for the results read.
 */
@Component
public class GameplayEventLogger {

    private static final Logger log = LoggerFactory.getLogger(GameplayEventLogger.class);

    @EventListener
    void on(QuestionStartedEvent event) {
        log.info("gameplay.question_opened sessionId={} questionId={} endsAt={}",
                event.sessionId(), event.questionId(), event.endsAt());
    }

    @EventListener
    void on(QuestionClosedEvent event) {
        log.info("gameplay.question_closed sessionId={} questionId={}",
                event.sessionId(), event.questionId());
    }

    @EventListener
    void on(AnswerRevealedEvent event) {
        log.info("gameplay.answer_revealed sessionId={} questionId={} correctOptionCount={}",
                event.sessionId(), event.questionId(), event.correctOptionIds().size());
    }

    @EventListener
    void on(LeaderboardUpdatedEvent event) {
        log.info("gameplay.leaderboard_shown sessionId={} standingsCount={}",
                event.sessionId(), event.leaderboard().size());
    }
}
