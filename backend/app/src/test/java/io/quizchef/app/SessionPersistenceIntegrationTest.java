package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.ParticipantState;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPin;
import io.quizchef.session.domain.SessionRosterEntry;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * Boots the application against a real PostgreSQL: Flyway V7 applies on top
 * of the existing schema, Hibernate validates the session mapping, and both
 * aggregates round-trip — including the durable-participant guarantee that a
 * disconnected participant reloads with its state, answers, and score intact.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SessionPersistenceIntegrationTest {

    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode KN = LanguageCode.of("kn");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPersistAndReloadSessionWithRoster() {
        Identity host = identityRepository.save(Identity.registered());
        UUID quizVersionId = UUID.randomUUID();

        Session session = Session.create(SessionPin.of("135790"), quizVersionId,
                host.reference(), new SessionSettings(true, false, true, 250));
        session.openLobby();
        UUID firstParticipant = UUID.randomUUID();
        UUID secondParticipant = UUID.randomUUID();
        session.registerParticipant(firstParticipant,
                ParticipantKey.forIdentity(new IdentityReference(UUID.randomUUID(),
                        io.quizchef.identity.domain.IdentityType.REGISTERED)));
        session.registerParticipant(secondParticipant,
                ParticipantKey.forGuest(GuestParticipantToken.of("guest-token-abc")));
        session.start();
        sessionRepository.save(session);

        transactionTemplate.executeWithoutResult(status -> {
            Session reloaded = sessionRepository.findById(session.getId()).orElseThrow();
            assertThat(reloaded.getState()).isEqualTo(SessionState.IN_PROGRESS);
            assertThat(reloaded.getPublishedQuizVersionId()).isEqualTo(quizVersionId);
            assertThat(reloaded.getHostIdentity()).isEqualTo(host.reference());
            assertThat(reloaded.getSessionSettings().maxParticipants()).isEqualTo(250);
            assertThat(reloaded.getSessionPin()).isEqualTo(SessionPin.of("135790"));
            assertThat(reloaded.roster())
                    .extracting(SessionRosterEntry::participantId, SessionRosterEntry::joinOrder)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(firstParticipant, 1),
                            org.assertj.core.groups.Tuple.tuple(secondParticipant, 2));
            assertThat(reloaded.roster().get(1).key().isGuest()).isTrue();
        });
    }

    @Test
    void shouldPersistMultipleParticipantsWithAnswersAndScore() {
        UUID sessionId = savedSessionId("246800");

        Participant registered = Participant.registered(sessionId,
                new IdentityReference(UUID.randomUUID(),
                        io.quizchef.identity.domain.IdentityType.REGISTERED), "Aman", EN);
        registered.connect(Instant.parse("2026-07-16T10:00:00Z"));
        registered.recordAnswer(new ParticipantAnswer(UUID.randomUUID(),
                Set.of(UUID.randomUUID(), UUID.randomUUID()), EN,
                Instant.parse("2026-07-16T10:00:05Z"), 1500, 900));

        Participant guest = Participant.guest(sessionId,
                GuestParticipantToken.generate(), "Guest", KN);

        participantRepository.save(registered);
        participantRepository.save(guest);

        transactionTemplate.executeWithoutResult(status -> {
            List<Participant> participants = participantRepository.findBySessionId(sessionId);
            assertThat(participants).hasSize(2);

            Participant reloaded = participantRepository.findById(registered.getId()).orElseThrow();
            assertThat(reloaded.isGuest()).isFalse();
            assertThat(reloaded.getTotalScore()).isEqualTo(900);
            assertThat(reloaded.answers()).singleElement().satisfies(answer -> {
                assertThat(answer.selectedOptionIds()).hasSize(2);
                assertThat(answer.answeredLanguage()).isEqualTo(EN);
                assertThat(answer.pointsAwarded()).isEqualTo(900);
            });
        });
    }

    @Test
    void disconnectedParticipantReloadsWithStateAndScoreIntact() {
        UUID sessionId = savedSessionId("975310");
        Participant participant = Participant.guest(sessionId,
                GuestParticipantToken.generate(), "Reconnecting Guest", KN);
        participant.connect(Instant.parse("2026-07-16T10:00:00Z"));
        participant.recordAnswer(new ParticipantAnswer(UUID.randomUUID(), Set.of(UUID.randomUUID()), KN,
                Instant.parse("2026-07-16T10:00:03Z"), 800, 650));
        participant.disconnect(Instant.parse("2026-07-16T10:01:00Z"));
        participantRepository.save(participant);

        transactionTemplate.executeWithoutResult(status -> {
            Participant reloaded = participantRepository.findById(participant.getId()).orElseThrow();
            // durable participant: a disconnect never deletes it or resets progress
            assertThat(reloaded.getState()).isEqualTo(ParticipantState.DISCONNECTED);
            assertThat(reloaded.isConnected()).isFalse();
            assertThat(reloaded.getTotalScore()).isEqualTo(650);
            assertThat(reloaded.answers()).hasSize(1);
            assertThat(reloaded.getLastSeenAt()).isEqualTo(Instant.parse("2026-07-16T10:01:00Z"));

            // and it can reconnect, keeping everything
            reloaded.connect(Instant.parse("2026-07-16T10:02:00Z"));
            assertThat(reloaded.isConnected()).isTrue();
            assertThat(reloaded.getTotalScore()).isEqualTo(650);
        });
    }

    private UUID savedSessionId(String pin) {
        Identity host = identityRepository.save(Identity.registered());
        Session session = Session.create(SessionPin.of(pin), UUID.randomUUID(),
                host.reference(), SessionSettings.defaults());
        return sessionRepository.save(session).getId();
    }
}
