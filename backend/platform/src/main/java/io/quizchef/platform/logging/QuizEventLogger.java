package io.quizchef.platform.logging;

import io.quizchef.quiz.domain.event.QuestionAddedToQuizEvent;
import io.quizchef.quiz.domain.event.QuestionArchivedEvent;
import io.quizchef.quiz.domain.event.QuestionPublishedEvent;
import io.quizchef.quiz.domain.event.QuizArchivedEvent;
import io.quizchef.quiz.domain.event.QuizCreatedEvent;
import io.quizchef.quiz.domain.event.QuizPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs quiz and question lifecycle transitions: create, publish, archive,
 * and composition.
 */
@Component
public class QuizEventLogger {

    private static final Logger log = LoggerFactory.getLogger(QuizEventLogger.class);

    @EventListener
    void on(QuizCreatedEvent event) {
        log.info("quiz.created quizId={} ownerId={}", event.quizId(), event.owner().identityId());
    }

    @EventListener
    void on(QuizPublishedEvent event) {
        log.info("quiz.published quizId={}", event.quizId());
    }

    @EventListener
    void on(QuizArchivedEvent event) {
        log.info("quiz.archived quizId={}", event.quizId());
    }

    @EventListener
    void on(QuestionAddedToQuizEvent event) {
        log.info("quiz.question_attached quizId={} questionId={}", event.quizId(), event.questionId());
    }

    @EventListener
    void on(QuestionPublishedEvent event) {
        log.info("question.published questionId={}", event.questionId());
    }

    @EventListener
    void on(QuestionArchivedEvent event) {
        log.info("question.archived questionId={}", event.questionId());
    }
}
