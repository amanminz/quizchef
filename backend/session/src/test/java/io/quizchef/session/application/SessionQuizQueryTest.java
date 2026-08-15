package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.quiz.application.GameplayQuestionContentQuery;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuestionContentView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableLocalizationView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableOptionTextView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableOptionView;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.session.domain.CorrectedOptionText;
import io.quizchef.session.domain.CorrectedPrompt;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionQuestionCorrection;
import io.quizchef.session.infrastructure.persistence.SessionQuestionCorrectionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The effective quiz: what one session is actually playing, once its
 * removals and corrections are applied.
 */
class SessionQuizQueryTest {

    private static final Instant AT = Instant.parse("2026-08-15T10:00:00Z");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode HI = LanguageCode.of("hi");

    private static final UUID Q1 = UUID.randomUUID();
    private static final UUID Q2 = UUID.randomUUID();
    private static final UUID Q3 = UUID.randomUUID();
    private static final UUID OPTION_A = UUID.randomUUID();
    private static final UUID OPTION_B = UUID.randomUUID();

    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final GameplayQuestionContentQuery contentQuery = mock(GameplayQuestionContentQuery.class);
    private final SessionQuestionCorrectionRepository correctionRepository =
            mock(SessionQuestionCorrectionRepository.class);
    private final SessionQuizQuery sessionQuizQuery =
            new SessionQuizQuery(gameplayQuizQuery, contentQuery, correctionRepository);

    @Test
    void aSessionWithNothingPulledOrFixedPlaysTheQuizAsAuthored() {
        Session session = inProgressSession();
        stubQuiz();

        assertThat(sessionQuizQuery.effectiveQuiz(session).questions())
                .extracting(PlayableQuestion::questionId)
                .containsExactly(Q1, Q2, Q3);
    }

    @Test
    void aRemovedQuestionIsSimplyNotInTheSequence() {
        Session session = inProgressSession();
        stubQuiz();
        session.removeQuestion(Q2, Set.of(Q1, Q2, Q3), null, 0, AT);

        PlayableQuizView effective = sessionQuizQuery.effectiveQuiz(session);

        assertThat(effective.questions()).extracting(PlayableQuestion::questionId)
                .containsExactly(Q1, Q3);
        // Which is the whole trick behind gapless numbering: the question
        // after the removed one moves up, and nothing downstream does any
        // arithmetic to make that happen.
        assertThat(QuestionProgression.numberOf(effective, session, Q3)).isEqualTo(2);
        assertThat(QuestionProgression.nextAfter(effective, session)).isPresent();
    }

    @Test
    void theHostsOwnViewKeepsRemovedQuestionsWhereTheyWere() {
        Session session = inProgressSession();
        stubQuiz();
        session.removeQuestion(Q2, Set.of(Q1, Q2, Q3), null, 0, AT);

        assertThat(sessionQuizQuery.quizIncludingRemoved(session).questions())
                .extracting(PlayableQuestion::questionId)
                .containsExactly(Q1, Q2, Q3);
    }

    @Test
    void aCorrectedAnswerKeyIsWhatTheEngineScoresAgainst() {
        Session session = inProgressSession();
        stubQuiz();
        when(correctionRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(correctionOf(session, Set.of(OPTION_B), List.of(), List.of())));

        assertThat(sessionQuizQuery.effectiveQuiz(session).questions())
                .filteredOn(question -> question.questionId().equals(Q1))
                .singleElement()
                .satisfies(question -> {
                    assertThat(question.correctOptionIds()).containsExactly(OPTION_B);
                    // The option set itself is never touched — answers
                    // already recorded point at these ids.
                    assertThat(question.allOptionIds()).containsExactlyInAnyOrder(OPTION_A, OPTION_B);
                });
    }

    @Test
    void correctedWordingOverlaysTheAuthoredText() {
        Session session = inProgressSession();
        stubContent();
        when(correctionRepository.findBySessionIdAndQuestionId(session.getId(), Q1))
                .thenReturn(Optional.of(correctionOf(session, Set.of(OPTION_A),
                        List.of(new CorrectedPrompt(EN, "Who wrote the Psalms, mostly?")),
                        List.of(new CorrectedOptionText(EN, OPTION_A, "David")))));

        PlayableQuestionContentView content = sessionQuizQuery.effectiveContent(session, Q1);

        assertThat(content.localizations())
                .filteredOn(localization -> localization.languageCode().equals("en"))
                .singleElement()
                .satisfies(localization -> {
                    assertThat(localization.prompt()).isEqualTo("Who wrote the Psalms, mostly?");
                    assertThat(localization.optionTexts())
                            .extracting(PlayableOptionTextView::text)
                            .containsExactly("David", "Authored B");
                });
    }

    @Test
    void aLanguageTheHostDidNotCorrectKeepsItsAuthoredText() {
        Session session = inProgressSession();
        stubContent();
        when(correctionRepository.findBySessionIdAndQuestionId(session.getId(), Q1))
                .thenReturn(Optional.of(correctionOf(session, Set.of(OPTION_A),
                        List.of(new CorrectedPrompt(EN, "Corrected English")),
                        List.of())));

        PlayableQuestionContentView content = sessionQuizQuery.effectiveContent(session, Q1);

        // Fixing the English answer key must not blank the Hindi — a room
        // reading in Hindi would simply lose the question.
        assertThat(content.localizations())
                .filteredOn(localization -> localization.languageCode().equals("hi"))
                .singleElement()
                .satisfies(localization -> {
                    assertThat(localization.prompt()).isEqualTo("Authored Hindi prompt");
                    assertThat(localization.optionTexts()).hasSize(2);
                });
    }

    @Test
    void aCorrectedPromptDropsAnExplanationWrittenForTheOldOne() {
        Session session = inProgressSession();
        stubContent();
        when(correctionRepository.findBySessionIdAndQuestionId(session.getId(), Q1))
                .thenReturn(Optional.of(correctionOf(session, Set.of(OPTION_B),
                        List.of(new CorrectedPrompt(EN, "Corrected English")), List.of())));

        PlayableQuestionContentView content = sessionQuizQuery.effectiveContent(session, Q1);

        // The author's explanation was written to justify an answer that is
        // no longer the answer; saying nothing beats saying the wrong thing.
        assertThat(content.localizations())
                .filteredOn(localization -> localization.languageCode().equals("en"))
                .singleElement()
                .satisfies(localization -> assertThat(localization.explanation()).isNull());
    }

    private Session inProgressSession() {
        Session session = sessionHostedBy(host(), "600001");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.previewQuestion(Q1, QuestionTimer.startingAt(AT, Duration.ofSeconds(5)));
        return session;
    }

    private void stubQuiz() {
        when(gameplayQuizQuery.load(any())).thenReturn(new PlayableQuizView(30, List.of(
                playable(Q1), playable(Q2), playable(Q3))));
    }

    private static PlayableQuestion playable(UUID questionId) {
        return new PlayableQuestion(questionId, Difficulty.EASY,
                Set.of(OPTION_A), Set.of(OPTION_A, OPTION_B));
    }

    private void stubContent() {
        when(contentQuery.content(Q1)).thenReturn(new PlayableQuestionContentView(
                Q1, QuestionType.SINGLE_CHOICE, "en",
                List.of(new PlayableOptionView(OPTION_A, 1), new PlayableOptionView(OPTION_B, 2)),
                List.of(
                        new PlayableLocalizationView("en", "Authored English prompt",
                                "Because the authored answer was A.",
                                List.of(new PlayableOptionTextView(OPTION_A, "Authored A"),
                                        new PlayableOptionTextView(OPTION_B, "Authored B"))),
                        new PlayableLocalizationView("hi", "Authored Hindi prompt", null,
                                List.of(new PlayableOptionTextView(OPTION_A, "हिन्दी A"),
                                        new PlayableOptionTextView(OPTION_B, "हिन्दी B"))))));
    }

    private static SessionQuestionCorrection correctionOf(Session session, Set<UUID> correctOptionIds,
                                                          List<CorrectedPrompt> prompts,
                                                          List<CorrectedOptionText> optionTexts) {
        List<CorrectedPrompt> withPrompt = prompts.isEmpty()
                ? List.of(new CorrectedPrompt(HI, "प्रश्न"))
                : prompts;
        return SessionQuestionCorrection.first(session.getId(), Q1, correctOptionIds,
                withPrompt, optionTexts, Set.of(OPTION_A, OPTION_B));
    }
}
