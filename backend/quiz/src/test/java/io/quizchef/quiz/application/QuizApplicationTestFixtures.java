package io.quizchef.quiz.application;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared builders for quiz application service tests.
 */
final class QuizApplicationTestFixtures {

    static final LanguageCode EN = LanguageCode.of("en");
    static final LanguageCode KN = LanguageCode.of("kn");

    private QuizApplicationTestFixtures() {
    }

    static CurrentUser quizMaster(UUID identityId) {
        return CurrentUser.authenticated(identityId, IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER));
    }

    static Quiz englishQuizOwnedBy(CurrentUser owner) {
        return Quiz.create(new QuizLocalization(EN, "Bible Quiz", "Sunday Youth Fellowship"),
                owner.reference());
    }

    static Question questionLocalizedIn(LanguageCode... languages) {
        Option trueOption = Option.of(true, 1);
        Option falseOption = Option.of(false, 2);
        Question question = Question.create(
                new QuestionLocalization(languages[0], "Jonah", "Jonah was swallowed by a great fish.", null),
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(trueOption, falseOption),
                List.of(trueOption.localized(languages[0], "True"),
                        falseOption.localized(languages[0], "False")));
        for (int i = 1; i < languages.length; i++) {
            question.localize(
                    new QuestionLocalization(languages[i], "Jonah", "Jonah prompt", null),
                    List.of(trueOption.localized(languages[i], "True"),
                            falseOption.localized(languages[i], "False")));
        }
        return question;
    }
}
