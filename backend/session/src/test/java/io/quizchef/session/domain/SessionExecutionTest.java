package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The gameplay phase machine: QUESTION_PREVIEW → QUESTION_OPEN →
 * QUESTION_CLOSED → ANSWER_REVEALED → LEADERBOARD, and back to a new
 * question's preview. Every transition is guarded (ADR-006).
 */
class SessionExecutionTest {

    private static final IdentityReference HOST =
            new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);
    private static final Instant NOW = Instant.parse("2026-07-16T10:00:00Z");

    private Session runningSession() {
        Session session = Session.create(SessionPin.of("123456"), UUID.randomUUID(), HOST,
                SessionSettings.defaults());
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        return session;
    }

    private static QuestionTimer previewTimer() {
        return QuestionTimer.startingAt(NOW, Duration.ofSeconds(5));
    }

    private static QuestionTimer answerTimer() {
        return QuestionTimer.startingAt(NOW, Duration.ofSeconds(30));
    }

    /** Previews, then opens, a question — the two-step entry every test needs. */
    private static void previewThenOpen(Session session, UUID questionId) {
        session.previewQuestion(questionId, previewTimer());
        session.openQuestion(answerTimer());
    }

    @Test
    void runsTheFullQuestionLoop() {
        Session session = runningSession();
        UUID questionId = UUID.randomUUID();

        session.previewQuestion(questionId, previewTimer());
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_PREVIEW);
        assertThat(session.getCurrentQuestionId()).isEqualTo(questionId);
        assertThat(session.acceptsAnswersFor(questionId)).isFalse();

        session.openQuestion(answerTimer());
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        assertThat(session.getCurrentQuestionId()).isEqualTo(questionId);
        assertThat(session.acceptsAnswersFor(questionId)).isTrue();

        session.closeQuestion();
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_CLOSED);
        assertThat(session.acceptsAnswersFor(questionId)).isFalse();

        session.revealAnswer();
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.ANSWER_REVEALED);

        session.showLeaderboard();
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.LEADERBOARD);

        UUID second = UUID.randomUUID();
        previewThenOpen(session, second);
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        assertThat(session.getCurrentQuestionId()).isEqualTo(second);
    }

    @Test
    void previewNeverAcceptsAnswers() {
        Session session = runningSession();
        UUID questionId = UUID.randomUUID();
        session.previewQuestion(questionId, previewTimer());

        assertThat(session.acceptsAnswersFor(questionId)).isFalse();
    }

    @Test
    void openingReplacesThePreviewTimerWithTheAnswerTimerButKeepsTheQuestion() {
        Session session = runningSession();
        UUID questionId = UUID.randomUUID();
        QuestionTimer preview = previewTimer();
        QuestionTimer answer = answerTimer();
        session.previewQuestion(questionId, preview);
        assertThat(session.getCurrentQuestionTimer()).isEqualTo(preview);

        session.openQuestion(answer);

        assertThat(session.getCurrentQuestionId()).isEqualTo(questionId);
        assertThat(session.getCurrentQuestionTimer()).isEqualTo(answer);
    }

    @Test
    void cannotOpenAQuestionWithoutFirstPreviewingIt() {
        Session session = runningSession();

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.openQuestion(answerTimer()));

        session.previewQuestion(UUID.randomUUID(), previewTimer());
        session.openQuestion(answerTimer());
        // Already open — opening again (skipping a fresh preview) is rejected.
        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.openQuestion(answerTimer()));
    }

    @Test
    void cannotPreviewTwiceInARow() {
        Session session = runningSession();
        session.previewQuestion(UUID.randomUUID(), previewTimer());

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.previewQuestion(UUID.randomUUID(), previewTimer()));
    }

    @Test
    void rejectsOutOfOrderPhaseTransitions() {
        Session session = runningSession();

        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::closeQuestion);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::revealAnswer);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::showLeaderboard);

        UUID questionId = UUID.randomUUID();
        previewThenOpen(session, questionId);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::revealAnswer);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::showLeaderboard);
        // cannot preview another question while one is open
        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.previewQuestion(UUID.randomUUID(), previewTimer()));
    }

    @Test
    void cannotPreviewAQuestionBeforeStarting() {
        Session session = Session.create(SessionPin.of("222222"), UUID.randomUUID(), HOST,
                SessionSettings.defaults());
        session.openLobby();

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.previewQuestion(UUID.randomUUID(), previewTimer()));
    }

    @Test
    void finishingClearsTheExecutionPointersRegardlessOfSubPhase() {
        Session previewing = runningSession();
        previewing.previewQuestion(UUID.randomUUID(), previewTimer());
        previewing.finish();
        assertThat(previewing.isFinished()).isTrue();
        assertThat(previewing.getCurrentPhase()).isNull();
        assertThat(previewing.getCurrentQuestionId()).isNull();
        assertThat(previewing.getCurrentQuestionTimer()).isNull();

        Session opened = runningSession();
        previewThenOpen(opened, UUID.randomUUID());
        opened.finish();
        assertThat(opened.isFinished()).isTrue();
        assertThat(opened.getCurrentPhase()).isNull();
        assertThat(opened.getCurrentQuestionId()).isNull();
        assertThat(opened.getCurrentQuestionTimer()).isNull();
    }

    @Test
    void acceptsAnswersOnlyForTheOpenQuestion() {
        Session session = runningSession();
        UUID questionId = UUID.randomUUID();
        previewThenOpen(session, questionId);

        assertThat(session.acceptsAnswersFor(questionId)).isTrue();
        assertThat(session.acceptsAnswersFor(UUID.randomUUID())).isFalse();
    }
}
