package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.session.domain.exception.DuplicateParticipantException;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.ParticipantAlreadyJoinedException;
import io.quizchef.session.domain.exception.SessionNotStartableException;
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
}
