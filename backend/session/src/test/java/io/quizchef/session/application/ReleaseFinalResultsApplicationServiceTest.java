package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.FinalResultsReleasedEvent;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReleaseFinalResultsApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final ReleaseFinalResultsApplicationService service = new ReleaseFinalResultsApplicationService(
            sessionRepository, authorizationService, eventPublisher, CLOCK);

    private final CurrentUser hostUser = host();

    private Session finishedSession() {
        Session session = sessionHostedBy(hostUser, "500001");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        session.finish();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void hostReleasesFinalStandings() {
        Session session = finishedSession();

        SessionSummaryView view = service.release(hostUser, session.getId());

        assertThat(view.finalResultsReleased()).isTrue();
        verify(authorizationService).authorize(hostUser, Permission.QUIZ_HOST);
        var event = org.mockito.ArgumentCaptor.forClass(FinalResultsReleasedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().sessionId()).isEqualTo(session.getId());
    }

    @Test
    void aDuplicateReleaseIsHarmlessAndDoesNotRepublish() {
        Session session = finishedSession();
        service.release(hostUser, session.getId());

        SessionSummaryView second = service.release(hostUser, session.getId());

        assertThat(second.finalResultsReleased()).isTrue();
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    void cannotReleaseBeforeTheSessionFinishes() {
        Session session = sessionHostedBy(hostUser, "500002");
        session.openLobby();
        session.registerParticipant(UUID.randomUUID(),
                ParticipantKey.forGuest(GuestParticipantToken.generate()));
        session.start();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(InvalidSessionTransitionException.class)
                .isThrownBy(() -> service.release(hostUser, session.getId()));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void onlyTheHostReleases() {
        Session session = finishedSession();

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.release(host(), session.getId()));
        verifyNoInteractions(eventPublisher);
    }
}
