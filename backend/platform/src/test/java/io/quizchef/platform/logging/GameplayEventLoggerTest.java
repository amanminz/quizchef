package io.quizchef.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.event.AnswerRevealedEvent;
import io.quizchef.session.domain.event.LeaderboardUpdatedEvent;
import io.quizchef.session.domain.event.QuestionClosedEvent;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameplayEventLoggerTest {

    private final GameplayEventLogger logger = new GameplayEventLogger();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();

    @Test
    void logsQuestionOpened() {
        Instant endsAt = Instant.now().plus(Duration.ofSeconds(20));
        try (LogCapture capture = new LogCapture(GameplayEventLogger.class)) {
            logger.on(new QuestionStartedEvent(sessionId, questionId, endsAt, 20, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("gameplay.question_opened"));
        }
    }

    @Test
    void logsQuestionClosed() {
        try (LogCapture capture = new LogCapture(GameplayEventLogger.class)) {
            logger.on(new QuestionClosedEvent(sessionId, questionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("gameplay.question_closed"));
        }
    }

    @Test
    void logsAnswerRevealedWithCorrectOptionCountNotContent() {
        try (LogCapture capture = new LogCapture(GameplayEventLogger.class)) {
            logger.on(new AnswerRevealedEvent(sessionId, questionId, Set.of(UUID.randomUUID()), Instant.now()));

            assertThat(capture.messages())
                    .anyMatch(m -> m.contains("gameplay.answer_revealed") && m.contains("correctOptionCount=1"));
        }
    }

    @Test
    void logsLeaderboardShownWithoutPerParticipantScores() {
        LeaderboardEntry entry = new LeaderboardEntry(UUID.randomUUID(), "Player", 100, 1);
        try (LogCapture capture = new LogCapture(GameplayEventLogger.class)) {
            logger.on(new LeaderboardUpdatedEvent(sessionId, List.of(entry), Instant.now()));

            assertThat(capture.messages()).anySatisfy(message -> {
                assertThat(message).contains("gameplay.leaderboard_shown");
                assertThat(message).contains("standingsCount=1");
                assertThat(message).doesNotContain("Player");
            });
        }
    }
}
