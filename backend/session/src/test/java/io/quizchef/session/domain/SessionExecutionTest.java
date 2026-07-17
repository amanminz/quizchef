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
 * The gameplay phase machine: QUESTION_OPEN → QUESTION_CLOSED →
 * ANSWER_REVEALED → LEADERBOARD, and back to a new question. Every
 * transition is guarded (ADR-006).
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

    private static QuestionTimer timer() {
        return QuestionTimer.startingAt(NOW, Duration.ofSeconds(30));
    }

    @Test
    void runsTheFullQuestionLoop() {
        Session session = runningSession();
        UUID questionId = UUID.randomUUID();

        session.openQuestion(questionId, timer());
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
        session.openQuestion(second, timer());
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
        assertThat(session.getCurrentQuestionId()).isEqualTo(second);
    }

    @Test
    void rejectsOutOfOrderPhaseTransitions() {
        Session session = runningSession();

        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::closeQuestion);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::revealAnswer);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::showLeaderboard);

        session.openQuestion(UUID.randomUUID(), timer());
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::revealAnswer);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::showLeaderboard);
        // cannot open another question while one is open
        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.openQuestion(UUID.randomUUID(), timer()));
    }

    @Test
    void cannotOpenAQuestionBeforeStarting() {
        Session session = Session.create(SessionPin.of("222222"), UUID.randomUUID(), HOST,
                SessionSettings.defaults());
        session.openLobby();

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.openQuestion(UUID.randomUUID(), timer()));
    }

    @Test
    void finishingClearsTheExecutionPointers() {
        Session session = runningSession();
        session.openQuestion(UUID.randomUUID(), timer());

        session.finish();

        assertThat(session.isFinished()).isTrue();
        assertThat(session.getCurrentPhase()).isNull();
        assertThat(session.getCurrentQuestionId()).isNull();
        assertThat(session.getCurrentQuestionTimer()).isNull();
    }

    @Test
    void acceptsAnswersOnlyForTheOpenQuestion() {
        Session session = runningSession();
        UUID questionId = UUID.randomUUID();
        session.openQuestion(questionId, timer());

        assertThat(session.acceptsAnswersFor(questionId)).isTrue();
        assertThat(session.acceptsAnswersFor(UUID.randomUUID())).isFalse();
    }
}
