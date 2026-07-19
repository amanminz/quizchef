package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The quiz module's identity boundary for gameplay: the participant-safe
 * display identity (today just the default-language title) of the quiz a
 * session runs. Participants must always know which quiz they joined, and
 * they read that through the session — never through the host-only quiz
 * management API — so the session module resolves the title through this
 * query instead of reaching into the quiz repository.
 */
@Service
public class QuizIdentityQuery {

    private final QuizRepository quizRepository;

    public QuizIdentityQuery(QuizRepository quizRepository) {
        this.quizRepository = quizRepository;
    }

    /** The quiz's default-localization title. */
    @Transactional(readOnly = true)
    public String quizTitle(UUID publishedQuizVersionId) {
        Quiz quiz = quizRepository.findById(publishedQuizVersionId)
                .orElseThrow(() -> new QuizNotFoundException(publishedQuizVersionId));
        return quiz.defaultLocalization().title();
    }
}
