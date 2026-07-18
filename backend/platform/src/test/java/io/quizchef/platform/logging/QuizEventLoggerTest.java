package io.quizchef.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.quiz.domain.event.QuestionAddedToQuizEvent;
import io.quizchef.quiz.domain.event.QuestionArchivedEvent;
import io.quizchef.quiz.domain.event.QuestionPublishedEvent;
import io.quizchef.quiz.domain.event.QuizArchivedEvent;
import io.quizchef.quiz.domain.event.QuizCreatedEvent;
import io.quizchef.quiz.domain.event.QuizPublishedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuizEventLoggerTest {

    private final QuizEventLogger logger = new QuizEventLogger();
    private final UUID quizId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final IdentityReference owner = new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);

    @Test
    void logsQuizCreated() {
        try (LogCapture capture = new LogCapture(QuizEventLogger.class)) {
            logger.on(new QuizCreatedEvent(quizId, owner, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("quiz.created") && m.contains(quizId.toString()));
        }
    }

    @Test
    void logsQuizPublished() {
        try (LogCapture capture = new LogCapture(QuizEventLogger.class)) {
            logger.on(new QuizPublishedEvent(quizId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("quiz.published"));
        }
    }

    @Test
    void logsQuizArchived() {
        try (LogCapture capture = new LogCapture(QuizEventLogger.class)) {
            logger.on(new QuizArchivedEvent(quizId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("quiz.archived"));
        }
    }

    @Test
    void logsQuestionAttached() {
        try (LogCapture capture = new LogCapture(QuizEventLogger.class)) {
            logger.on(new QuestionAddedToQuizEvent(quizId, questionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("quiz.question_attached"));
        }
    }

    @Test
    void logsQuestionPublished() {
        try (LogCapture capture = new LogCapture(QuizEventLogger.class)) {
            logger.on(new QuestionPublishedEvent(questionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("question.published"));
        }
    }

    @Test
    void logsQuestionArchived() {
        try (LogCapture capture = new LogCapture(QuizEventLogger.class)) {
            logger.on(new QuestionArchivedEvent(questionId, Instant.now()));

            assertThat(capture.messages()).anyMatch(m -> m.contains("question.archived"));
        }
    }
}
