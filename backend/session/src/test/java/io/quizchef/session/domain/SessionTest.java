package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.session.domain.exception.DuplicateParticipantException;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.ParticipantAlreadyJoinedException;
import io.quizchef.session.domain.exception.QuestionRemovalNotAllowedException;
import io.quizchef.session.domain.exception.SessionNotStartableException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final IdentityReference HOST =
            new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);
    private static final UUID QUIZ_VERSION = UUID.randomUUID();

    private Session session() {
        return Session.create(SessionPin.of("123456"), QUIZ_VERSION, HOST, SessionSettings.defaults());
    }

    private static ParticipantKey guestKey() {
        return ParticipantKey.forGuest(GuestParticipantToken.generate());
    }

    @Test
    void shouldCreateInCreatedStateReferencingAPublishedQuizVersion() {
        Session session = session();

        assertThat(session.getId()).isNotNull();
        assertThat(session.getState()).isEqualTo(SessionState.CREATED);
        assertThat(session.getPublishedQuizVersionId()).isEqualTo(QUIZ_VERSION);
        assertThat(session.getHostIdentity()).isEqualTo(HOST);
        assertThat(session.getCurrentQuestionId()).isNull();
        assertThat(session.getCurrentPhase()).isNull();
        assertThat(session.roster()).isEmpty();
    }

    @Test
    void progressesThroughTheFullLifecycle() {
        Session session = session();

        session.openLobby();
        assertThat(session.isInLobby()).isTrue();

        session.registerParticipant(UUID.randomUUID(), guestKey());
        session.start();
        assertThat(session.isInProgress()).isTrue();

        session.finish();
        assertThat(session.isFinished()).isTrue();

        session.archive();
        assertThat(session.isArchived()).isTrue();
    }

    @Test
    void finalResultsStartUnreleasedAndOnlyReleaseAfterFinishing() {
        Session session = session();
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(), guestKey());
        session.start();
        assertThat(session.isFinalResultsReleased()).isFalse();

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(session::releaseFinalResults);

        session.finish();
        assertThat(session.isFinalResultsReleased()).isFalse();

        session.releaseFinalResults();
        assertThat(session.isFinalResultsReleased()).isTrue();

        // Idempotent: a duplicate release is harmless, not a conflict.
        session.releaseFinalResults();
        assertThat(session.isFinalResultsReleased()).isTrue();
    }

    @Test
    void rejectsOutOfOrderTransitions() {
        Session session = session();

        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::start);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::finish);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::archive);

        session.openLobby();
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::openLobby);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::finish);
    }

    @Test
    void cannotStartWithoutParticipants() {
        Session session = session();
        session.openLobby();

        assertThatExceptionOfType(SessionNotStartableException.class).isThrownBy(session::start);
    }

    @Test
    void assignsSequentialJoinOrder() {
        Session session = session();
        session.openLobby();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        session.registerParticipant(first, guestKey());
        session.registerParticipant(second, guestKey());

        assertThat(session.roster())
                .extracting(SessionRosterEntry::participantId, SessionRosterEntry::joinOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, 1),
                        org.assertj.core.groups.Tuple.tuple(second, 2));
    }

    @Test
    void rejectsDuplicateParticipantId() {
        Session session = session();
        session.openLobby();
        UUID participantId = UUID.randomUUID();
        session.registerParticipant(participantId, guestKey());

        assertThatExceptionOfType(DuplicateParticipantException.class)
                .isThrownBy(() -> session.registerParticipant(participantId, guestKey()));
    }

    @Test
    void rejectsSameIdentityOrTokenTwice() {
        Session session = session();
        session.openLobby();
        IdentityReference identity = new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);
        session.registerParticipant(UUID.randomUUID(), ParticipantKey.forIdentity(identity));

        assertThatExceptionOfType(ParticipantAlreadyJoinedException.class)
                .isThrownBy(() -> session.registerParticipant(
                        UUID.randomUUID(), ParticipantKey.forIdentity(identity)));

        GuestParticipantToken token = GuestParticipantToken.of("shared-token");
        session.registerParticipant(UUID.randomUUID(), ParticipantKey.forGuest(token));
        assertThatExceptionOfType(ParticipantAlreadyJoinedException.class)
                .isThrownBy(() -> session.registerParticipant(
                        UUID.randomUUID(), ParticipantKey.forGuest(GuestParticipantToken.of("shared-token"))));
    }

    @Test
    void lateJoinIsAllowedOnlyWhenEnabled() {
        Session lateJoinOff = Session.create(SessionPin.of("222222"), QUIZ_VERSION, HOST,
                new SessionSettings(false, true, true, 100));
        lateJoinOff.openLobby();
        lateJoinOff.registerParticipant(UUID.randomUUID(), guestKey());
        lateJoinOff.start();
        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> lateJoinOff.registerParticipant(UUID.randomUUID(), guestKey()));

        Session lateJoinOn = session();
        lateJoinOn.openLobby();
        lateJoinOn.registerParticipant(UUID.randomUUID(), guestKey());
        lateJoinOn.start();
        lateJoinOn.registerParticipant(UUID.randomUUID(), guestKey());
        assertThat(lateJoinOn.participantCount()).isEqualTo(2);
    }

    @Test
    void finishedAndArchivedSessionsAreImmutable() {
        Session session = session();
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(), guestKey());
        session.start();
        session.finish();

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> session.registerParticipant(UUID.randomUUID(), guestKey()));
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::start);

        session.archive();
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::archive);
        assertThatExceptionOfType(InvalidSessionTransitionException.class).isThrownBy(session::finish);
    }

    // --- Correcting and removing questions mid-session ---

    private static final Instant AT = Instant.parse("2026-08-15T10:00:00Z");
    private static final UUID Q1 = UUID.randomUUID();
    private static final UUID Q2 = UUID.randomUUID();
    private static final UUID Q3 = UUID.randomUUID();
    private static final Set<UUID> THREE_QUESTIONS = new LinkedHashSet<>(java.util.List.of(Q1, Q2, Q3));

    private Session inProgressSession() {
        Session session = session();
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(), guestKey());
        session.start();
        return session;
    }

    private static QuestionTimer timer() {
        return QuestionTimer.startingAt(AT, Duration.ofSeconds(30));
    }

    @Test
    void removingAQuestionRecordsWhatWasPulledAndWhenceWithoutErasingIt() {
        Session session = inProgressSession();
        session.previewQuestion(Q1, timer());
        session.openQuestion(timer());

        assertThat(session.removeQuestion(Q1, THREE_QUESTIONS, SessionPhase.QUESTION_OPEN, 4, AT))
                .isTrue();

        assertThat(session.isRemoved(Q1)).isTrue();
        assertThat(session.removedQuestionIds()).containsExactly(Q1);
        // A marker, not a deletion: the host's audit entry outlives the game.
        assertThat(session.removedQuestions()).singleElement().satisfies(removed -> {
            assertThat(removed.questionId()).isEqualTo(Q1);
            assertThat(removed.removedAt()).isEqualTo(AT);
            assertThat(removed.removedFromPhase()).isEqualTo(SessionPhase.QUESTION_OPEN);
            assertThat(removed.cancelledAnswerCount()).isEqualTo(4);
        });
    }

    @Test
    void removingTheSameQuestionTwiceIsAConvergentNoOp() {
        Session session = inProgressSession();
        session.removeQuestion(Q2, THREE_QUESTIONS, null, 0, AT);

        // A host double-click must not throw and must not undo the first.
        assertThat(session.removeQuestion(Q2, THREE_QUESTIONS, null, 0, AT.plusSeconds(1))).isFalse();
        assertThat(session.removedQuestions()).hasSize(1);
        assertThat(session.removedQuestions().getFirst().removedAt()).isEqualTo(AT);
    }

    @Test
    void refusesToRemoveTheLastQuestionLeftToPlay() {
        Session session = inProgressSession();

        // A session with nothing left cannot produce standings for a quiz
        // nobody played, so the safe outcome is to refuse.
        assertThatExceptionOfType(QuestionRemovalNotAllowedException.class)
                .isThrownBy(() -> session.removeQuestion(Q1, Set.of(Q1), null, 0, AT));
        assertThat(session.removedQuestionIds()).isEmpty();
    }

    @Test
    void refusesToRemoveAQuestionThisSessionIsNotPlaying() {
        Session session = inProgressSession();

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                session.removeQuestion(UUID.randomUUID(), THREE_QUESTIONS, null, 0, AT));
    }

    @Test
    void cancellingTheCurrentQuestionLeavesItReplayable() {
        Session session = inProgressSession();
        session.previewQuestion(Q1, timer());
        session.openQuestion(timer());

        session.cancelCurrentQuestion();

        // The phase and its clock are gone; where the engine stands is not.
        assertThat(session.getCurrentPhase()).isNull();
        assertThat(session.getCurrentQuestionTimer()).isNull();
        assertThat(session.getCurrentQuestionId()).isEqualTo(Q1);
        assertThat(session.acceptsAnswersFor(Q1)).isFalse();

        // And that is exactly what makes the corrected question replayable.
        session.previewQuestion(Q1, timer());
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_PREVIEW);
    }

    @Test
    void cancellingTheCurrentQuestionLetsTheEngineStepPastItToTheNext() {
        Session session = inProgressSession();
        session.previewQuestion(Q1, timer());
        session.openQuestion(timer());

        session.removeQuestion(Q1, THREE_QUESTIONS, SessionPhase.QUESTION_OPEN, 0, AT);
        session.cancelCurrentQuestion();
        session.previewQuestion(Q2, timer());

        assertThat(session.getCurrentQuestionId()).isEqualTo(Q2);
        assertThat(session.getCurrentPhase()).isEqualTo(SessionPhase.QUESTION_PREVIEW);
    }

    @Test
    void cancellingBetweenQuestionsIsHarmless() {
        Session session = inProgressSession();

        session.cancelCurrentQuestion();

        assertThat(session.getCurrentPhase()).isNull();
        assertThat(session.getCurrentQuestionId()).isNull();
    }
}
