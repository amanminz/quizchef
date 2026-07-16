package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.domain.exception.QuizNotPublishedException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The quiz module's answer to "may this content be run in a session?" — the
 * clean boundary other modules use instead of reaching into the quiz
 * repository. Today a published quiz version id is the quiz's own id and
 * "published" means the quiz's state; when quiz revisions land, this
 * resolves the specific immutable version.
 */
@Service
public class QuizPublicationQuery {

    private final QuizRepository quizRepository;

    public QuizPublicationQuery(QuizRepository quizRepository) {
        this.quizRepository = quizRepository;
    }

    /**
     * Ensures the referenced quiz version exists and is published, or throws:
     * 404 if it does not exist, 409 if it is not published.
     */
    @Transactional(readOnly = true)
    public void requirePublished(UUID publishedQuizVersionId) {
        Quiz quiz = quizRepository.findById(publishedQuizVersionId)
                .orElseThrow(() -> new QuizNotFoundException(publishedQuizVersionId));
        if (!quiz.isPublished()) {
            throw new QuizNotPublishedException(publishedQuizVersionId);
        }
    }
}
