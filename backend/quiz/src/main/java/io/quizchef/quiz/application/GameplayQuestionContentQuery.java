package io.quizchef.quiz.application;

import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableLocalizationView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableOptionTextView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableOptionView;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.OptionLocalization;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The quiz module's display boundary for gameplay: hands the session module
 * a question's participant-safe content (prompt and options, every
 * language) so it can be served to the players of a running session. No
 * ownership check by design — the caller is the session engine, whose
 * session already references the published quiz; the quiz module is not
 * the place to re-derive that authority. Sanitization is structural: the
 * returned view has no field that could carry correctness or explanations.
 */
@Service
public class GameplayQuestionContentQuery {

    private final QuestionRepository questionRepository;

    public GameplayQuestionContentQuery(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public PlayableQuestionContentView content(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));

        List<PlayableOptionView> options = question.options().stream()
                .sorted(Comparator.comparingInt(Option::displayOrder))
                .map(option -> new PlayableOptionView(option.id(), option.displayOrder()))
                .toList();

        List<PlayableLocalizationView> localizations = question.localizations().stream()
                .map(localization -> toLocalization(question, localization))
                .toList();

        return new PlayableQuestionContentView(
                question.getId(),
                question.getQuestionType(),
                question.getDefaultLanguage().value(),
                options,
                localizations);
    }

    private static PlayableLocalizationView toLocalization(Question question,
                                                           QuestionLocalization localization) {
        List<PlayableOptionTextView> optionTexts = question
                .optionLocalizations(localization.languageCode()).stream()
                .map(text -> new PlayableOptionTextView(text.optionId(), text.text()))
                .toList();
        return new PlayableLocalizationView(
                localization.languageCode().value(),
                localization.prompt(),
                optionTexts);
    }
}
