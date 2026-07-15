package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.quiz.domain.BibleReference;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.MediaReference;
import io.quizchef.quiz.domain.MediaType;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Quiz;
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
 * Boots the application against a real PostgreSQL: Flyway V3 applies,
 * Hibernate validates the quiz schema, and both aggregates round-trip with
 * their embedded value objects and collections.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class QuizPersistenceIntegrationTest {

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
        Question question = Question.create(
                "Exodus leader", "Who led Israel out of Egypt?", "See Exodus 3.",
                QuestionType.SINGLE_CHOICE, Difficulty.EASY,
                List.of(Option.of("Moses", true, 1), Option.of("Aaron", false, 2)));
        question.updateBibleReferences(List.of(new BibleReference("Exodus", 3, 1, 10, "ESV")));
        question.updateMediaReferences(List.of(
                MediaReference.of(MediaType.IMAGE, "media/burning-bush.png", "Burning bush", 1)));
        questionRepository.save(question);

        transactionTemplate.executeWithoutResult(status -> {
            Question reloaded = questionRepository.findById(question.getId()).orElseThrow();
            assertThat(reloaded.getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
            assertThat(reloaded.getDifficulty()).isEqualTo(Difficulty.EASY);
            assertThat(reloaded.options())
                    .extracting(Option::text, Option::correct)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Moses", true),
                            org.assertj.core.groups.Tuple.tuple("Aaron", false));
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

        Quiz quiz = Quiz.create("BELC Bible Quiz", "Weekly quiz", ownerReference);
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
    void questionsRemainReusableAcrossQuizzes() {
        Identity owner = identityRepository.save(Identity.registered());
        Question shared = questionRepository.save(sampleQuestion("Shared"));

        Quiz quizA = Quiz.create("Quiz A", null, owner.reference());
        quizA.addQuestion(shared.getId());
        Quiz quizB = Quiz.create("Quiz B", null, owner.reference());
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
        return Question.create(
                title, "Prompt for " + title, null,
                QuestionType.TRUE_FALSE, Difficulty.MEDIUM,
                List.of(Option.of("True", true, 1), Option.of("False", false, 2)));
    }
}
