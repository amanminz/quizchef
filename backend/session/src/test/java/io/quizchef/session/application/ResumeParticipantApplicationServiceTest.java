package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.GuestTokenDigest;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Returning to a session you are already in — and, just as importantly,
 * every claim that is refused.
 */
class ResumeParticipantApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LanguageCode HI = LanguageCode.of("hi");
    private static final String PIN = "900001";

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);

    private final ResumeParticipantApplicationService service = new ResumeParticipantApplicationService(
            sessionRepository, participantRepository, new SessionSnapshotAssembler(CLOCK),
            eventPublisher, CLOCK);

    @Test
    void aGuestResumesWithTheirScoreAnswersNameAndLanguageIntact() {
        Session session = lobbySession();
        GuestParticipantToken token = GuestParticipantToken.generate();
        Participant participant = guestIn(session, token, "Aman");
        // The real shape of the bug: joined, played, then the phone slept.
        participant.connect(NOW);
        participant.recordAnswer(new ParticipantAnswer(UUID.randomUUID(),
                Set.of(UUID.randomUUID()), HI, NOW, 800, 940));
        participant.disconnect(NOW);
        stubGuest(session, token, participant);

        SessionSnapshotView snapshot = service.resume(CurrentUser.anonymous(),
                new ResumeParticipantCommand(PIN, token.value()));

        assertThat(snapshot.participantId()).isEqualTo(participant.getId());
        assertThat(snapshot.participantScore()).isEqualTo(940);
        assertThat(snapshot.displayName()).isEqualTo("Aman");
        assertThat(snapshot.preferredLanguage()).isEqualTo("hi");
        assertThat(participant.answers()).hasSize(1);
        assertThat(participant.isConnected()).isTrue();
        verify(eventPublisher).publish(any(ParticipantReconnectedEvent.class));
    }

    @Test
    void aResumeReturnsTheSameParticipantRatherThanCreatingOne() {
        Session session = lobbySession();
        GuestParticipantToken token = GuestParticipantToken.generate();
        Participant participant = guestIn(session, token, "Aman");
        stubGuest(session, token, participant);

        service.resume(CurrentUser.anonymous(), new ResumeParticipantCommand(PIN, token.value()));
        service.resume(CurrentUser.anonymous(), new ResumeParticipantCommand(PIN, token.value()));

        // Resuming twice — two tabs, or a refresh racing a reconnect — is
        // idempotent: one roster entry, one participant, one score.
        assertThat(session.participantCount()).isEqualTo(1);
        assertThat(participant.isConnected()).isTrue();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void resumeNeverWritesTheSessionsOwnPhase() {
        Session session = lobbySession();
        GuestParticipantToken token = GuestParticipantToken.generate();
        Participant participant = guestIn(session, token, "Aman");
        stubGuest(session, token, participant);

        service.resume(CurrentUser.anonymous(), new ResumeParticipantCommand(PIN, token.value()));

        // A resume arriving mid-question-transition must not roll the game
        // back. It cannot: it only ever touches the participant.
        assertThat(session.getState()).isEqualTo(SessionState.LOBBY);
        assertThat(session.getCurrentPhase()).isNull();
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void anUnknownTokenIsRefused() {
        Session session = lobbySession();
        when(participantRepository.findBySessionIdAndGuestTokenDigestValue(eq(session.getId()), any()))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(ParticipantNotFoundException.class).isThrownBy(() ->
                service.resume(CurrentUser.anonymous(),
                        new ResumeParticipantCommand(PIN, GuestParticipantToken.generate().value())));
    }

    @Test
    void aTokenFromAnotherSessionDoesNotResolveInThisOne() {
        Session session = lobbySession();
        GuestParticipantToken otherSessionsToken = GuestParticipantToken.generate();
        // The lookup is scoped by session, so a credential issued elsewhere
        // simply is not there — it cannot be replayed across quizzes, and it
        // cannot restore someone into a session they never joined.
        when(participantRepository.findBySessionIdAndGuestTokenDigestValue(
                session.getId(), GuestTokenDigest.of(otherSessionsToken).value()))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(ParticipantNotFoundException.class).isThrownBy(() ->
                service.resume(CurrentUser.anonymous(),
                        new ResumeParticipantCommand(PIN, otherSessionsToken.value())));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void aParticipantIdIsNotACredential() {
        Session session = lobbySession();
        GuestParticipantToken token = GuestParticipantToken.generate();
        Participant participant = guestIn(session, token, "Aman");
        stubGuest(session, token, participant);

        // The id identifies; it does not authenticate. Presenting one as a
        // token digests to something no participant matches — and there is
        // no field on the command that would accept it as proof anyway.
        assertThatExceptionOfType(ParticipantNotFoundException.class).isThrownBy(() ->
                service.resume(CurrentUser.anonymous(),
                        new ResumeParticipantCommand(PIN, participant.getId().toString())));
    }

    @Test
    void aNameIsNotACredentialEither() {
        Session session = lobbySession();
        GuestParticipantToken token = GuestParticipantToken.generate();
        guestIn(session, token, "Aman");

        // Someone typing the same name has nothing to present. Anonymous and
        // token-less, the only honest answer is "prove it or join".
        assertThatExceptionOfType(UnauthorizedException.class).isThrownBy(() ->
                service.resume(CurrentUser.anonymous(), new ResumeParticipantCommand(PIN, null)));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void aRegisteredPlayerResumesOnTheirIdentityWithoutAnyToken() {
        Session session = lobbySession();
        CurrentUser player = CurrentUser.authenticated(
                UUID.randomUUID(), IdentityType.REGISTERED, Set.of(Role.USER));
        Participant participant = Participant.registered(
                session.getId(), player.reference(), "Aman", HI);
        session.registerParticipant(participant.getId(), participant.key());
        when(participantRepository.findBySessionIdAndIdentityReferenceIdentityId(
                session.getId(), player.identityId())).thenReturn(Optional.of(participant));

        SessionSnapshotView snapshot =
                service.resume(player, new ResumeParticipantCommand(PIN, null));

        assertThat(snapshot.participantId()).isEqualTo(participant.getId());
        assertThat(participant.isConnected()).isTrue();
    }

    // --- fixtures ---

    private Session lobbySession() {
        Session session = sessionHostedBy(host(), PIN);
        session.openLobby();
        when(sessionRepository.findAndLockBySessionPinValueAndStateNot(PIN, SessionState.ARCHIVED))
                .thenReturn(Optional.of(session));
        return session;
    }

    private static Participant guestIn(Session session, GuestParticipantToken token, String name) {
        Participant participant = Participant.guest(session.getId(), token, name, HI);
        session.registerParticipant(participant.getId(), participant.key());
        return participant;
    }

    private void stubGuest(Session session, GuestParticipantToken token, Participant participant) {
        when(participantRepository.findBySessionIdAndGuestTokenDigestValue(
                session.getId(), GuestTokenDigest.of(token).value()))
                .thenReturn(Optional.of(participant));
    }
}
