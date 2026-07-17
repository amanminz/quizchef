package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizQuestion;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The quiz module's gameplay boundary: hands the session engine everything it
 * needs to run a quiz, and nothing it does not. Other modules read a quiz for
 * play through this application service, never through the quiz repository.
 */
@Service
public class GameplayQuizQuery {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    public GameplayQuizQuery(QuizRepository quizRepository, QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public PlayableQuizView load(UUID publishedQuizVersionId) {
        Quiz quiz = quizRepository.findById(publishedQuizVersionId)
                .orElseThrow(() -> new QuizNotFoundException(publishedQuizVersionId));

        List<UUID> orderedQuestionIds = quiz.questions().stream()
                .map(QuizQuestion::questionId)
                .toList();
        Map<UUID, Question> questionsById = questionRepository.findAllById(orderedQuestionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<PlayableQuizView.PlayableQuestion> playable = orderedQuestionIds.stream()
                .map(questionsById::get)
                .filter(java.util.Objects::nonNull)
                .map(GameplayQuizQuery::toPlayable)
                .toList();

        return new PlayableQuizView(quiz.getSettings().questionTimeLimitSeconds(), playable);
    }

    private static PlayableQuizView.PlayableQuestion toPlayable(Question question) {
        Set<UUID> correct = new LinkedHashSet<>();
        Set<UUID> all = new LinkedHashSet<>();
        for (Option option : question.options()) {
            all.add(option.id());
            if (option.correct()) {
                correct.add(option.id());
            }
        }
        return new PlayableQuizView.PlayableQuestion(
                question.getId(), question.getDifficulty(), correct, all);
    }
}
