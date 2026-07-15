package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.quiz.domain.BibleReference;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.MediaReference;
import io.quizchef.quiz.domain.MediaType;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.OptionLocalization;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import io.quizchef.quiz.domain.QuizQuestion;
import io.quizchef.quiz.domain.QuizSettings;
import io.quizchef.quiz.domain.QuizState;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the application against a real PostgreSQL: Flyway V3+V4 apply,
 * Hibernate validates the quiz schema, and both aggregates round-trip with
 * their embedded value objects, localizations, and collections.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class QuizPersistenceIntegrationTest {

    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode KN = LanguageCode.of("kn");
    private static final LanguageCode HI = LanguageCode.of("hi");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPersistAndReloadQuestionWithAllValueObjects() {
        Option moses = Option.of(true, 1);
        Option aaron = Option.of(false, 2);
        Question question = Question.create(
                new QuestionLocalization(EN, "Exodus leader", "Who led Israel out of Egypt?", "See Exodus 3."),
                identityRepository.save(Identity.registered()).reference(),
                QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                List.of(moses, aaron),
                List.of(moses.localized(EN, "Moses"), aaron.localized(EN, "Aaron")));
        question.updateBibleReferences(List.of(new BibleReference("Exodus", 3, 1, 10, "ESV")));
        question.updateMediaReferences(List.of(
                MediaReference.of(MediaType.IMAGE, "media/burning-bush.png", "Burning bush", 1)));
        questionRepository.save(question);

        transactionTemplate.executeWithoutResult(status -> {
            Question reloaded = questionRepository.findById(question.getId()).orElseThrow();
            assertThat(reloaded.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
            assertThat(reloaded.getDifficulty()).isEqualTo(Difficulty.EASY);
            assertThat(reloaded.getDefaultLanguage()).isEqualTo(EN);
            assertThat(reloaded.defaultLocalization().prompt()).isEqualTo("Who led Israel out of Egypt?");
            assertThat(reloaded.options())
                    .extracting(Option::correct)
                    .containsExactly(true, false);
            assertThat(reloaded.optionLocalizations(EN))
                    .extracting(OptionLocalization::text)
                    .containsExactly("Moses", "Aaron");
            assertThat(reloaded.bibleReferences())
                    .containsExactly(new BibleReference("Exodus", 3, 1, 10, "ESV"));
            assertThat(reloaded.mediaReferences()).hasSize(1);
            assertThat(reloaded.mediaReferences().getFirst().storageKey())
                    .isEqualTo("media/burning-bush.png");
            assertThat(reloaded.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void shouldPersistQuizWithOwnerSettingsAndOrderedComposition() {
        Identity owner = identityRepository.save(Identity.registered());
        IdentityReference ownerReference = owner.reference();

        Question first = questionRepository.save(sampleQuestion("First"));
        Question second = questionRepository.save(sampleQuestion("Second"));

        Quiz quiz = Quiz.create(new QuizLocalization(EN, "BELC Bible Quiz", "Weekly quiz"), ownerReference);
        quiz.updateSettings(new QuizSettings(true, false, 45, true, false));
        quiz.addQuestion(first.getId());
        quiz.addQuestion(second.getId());
        quiz.publish();
        quizRepository.save(quiz);

        transactionTemplate.executeWithoutResult(status -> {
            Quiz reloaded = quizRepository.findById(quiz.getId()).orElseThrow();
            assertThat(reloaded.getOwnerIdentity()).isEqualTo(ownerReference);
            assertThat(reloaded.getState()).isEqualTo(QuizState.PUBLISHED);
            assertThat(reloaded.getSettings().questionTimeLimitSeconds()).isEqualTo(45);
            assertThat(reloaded.getSettings().randomizeQuestionOrder()).isTrue();
            assertThat(reloaded.questions())
                    .extracting(QuizQuestion::questionId, QuizQuestion::displayOrder)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(first.getId(), 1),
                            org.assertj.core.groups.Tuple.tuple(second.getId(), 2));
        });
    }

    @Test
    void shouldPersistAndReloadMultipleTranslations() {
        Identity owner = identityRepository.save(Identity.registered());

        Option trueOption = Option.of(true, 1);
        Option falseOption = Option.of(false, 2);
        Question question = Question.create(
                new QuestionLocalization(EN, "Jonah", "Jonah was swallowed by a great fish.", null),
                owner.reference(),
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(trueOption, falseOption),
                List.of(trueOption.localized(EN, "True"), falseOption.localized(EN, "False")));
        question.localize(
                new QuestionLocalization(KN, "ಯೋನ", "ಯೋನನನ್ನು ದೊಡ್ಡ ಮೀನು ನುಂಗಿತು.", null),
                List.of(trueOption.localized(KN, "ಸರಿ"), falseOption.localized(KN, "ತಪ್ಪು")));
        questionRepository.save(question);

        Quiz quiz = Quiz.create(new QuizLocalization(EN, "BELC Bible Quiz", "Weekly quiz"),
                owner.reference());
        quiz.localize(new QuizLocalization(KN, "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", "ವಾರದ ರಸಪ್ರಶ್ನೆ"));
        quiz.localize(new QuizLocalization(HI, "बाइबल प्रश्नोत्तरी", null));
        quiz.addQuestion(question.getId());
        quizRepository.save(quiz);

        transactionTemplate.executeWithoutResult(status -> {
            Quiz reloadedQuiz = quizRepository.findById(quiz.getId()).orElseThrow();
            assertThat(reloadedQuiz.getDefaultLanguage()).isEqualTo(EN);
            assertThat(reloadedQuiz.localizations()).hasSize(3);
            assertThat(reloadedQuiz.localization(KN).orElseThrow().title()).isEqualTo("ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ");
            assertThat(reloadedQuiz.localization(HI).orElseThrow().description()).isNull();

            Question reloadedQuestion = questionRepository.findById(question.getId()).orElseThrow();
            assertThat(reloadedQuestion.localizations()).hasSize(2);
            assertThat(reloadedQuestion.localization(KN).orElseThrow().prompt())
                    .isEqualTo("ಯೋನನನ್ನು ದೊಡ್ಡ ಮೀನು ನುಂಗಿತು.");
            assertThat(reloadedQuestion.optionLocalizations(KN))
                    .extracting(OptionLocalization::text)
                    .containsExactly("ಸರಿ", "ತಪ್ಪು");
            // structure is shared across translations: same option ids, same correctness
            assertThat(reloadedQuestion.optionLocalizations(KN))
                    .extracting(OptionLocalization::optionId)
                    .containsExactlyElementsOf(reloadedQuestion.optionLocalizations(EN).stream()
                            .map(OptionLocalization::optionId)
                            .toList());
        });
    }

    @Test
    void questionsRemainReusableAcrossQuizzes() {
        Identity owner = identityRepository.save(Identity.registered());
        Question shared = questionRepository.save(sampleQuestion("Shared"));

        Quiz quizA = Quiz.create(new QuizLocalization(EN, "Quiz A", null), owner.reference());
        quizA.addQuestion(shared.getId());
        Quiz quizB = Quiz.create(new QuizLocalization(EN, "Quiz B", null), owner.reference());
        quizB.addQuestion(shared.getId());
        quizRepository.save(quizA);
        quizRepository.save(quizB);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(quizRepository.findById(quizA.getId()).orElseThrow().containsQuestion(shared.getId()))
                    .isTrue();
            assertThat(quizRepository.findById(quizB.getId()).orElseThrow().containsQuestion(shared.getId()))
                    .isTrue();
        });
    }

    private Question sampleQuestion(String title) {
        Option trueOption = Option.of(true, 1);
        Option falseOption = Option.of(false, 2);
        return Question.create(
                new QuestionLocalization(EN, title, "Prompt for " + title, null),
                identityRepository.save(Identity.registered()).reference(),
                QuestionType.TRUE_FALSE, Difficulty.MEDIUM,
                List.of(trueOption, falseOption),
                List.of(trueOption.localized(EN, "True"), falseOption.localized(EN, "False")));
    }
}
