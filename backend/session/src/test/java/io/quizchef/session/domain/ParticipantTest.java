package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.exception.InvalidParticipantTransitionException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode KN = LanguageCode.of("kn");
    private static final Instant T1 = Instant.parse("2026-07-16T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-16T10:05:00Z");

    private static Participant registered() {
        return Participant.registered(SESSION_ID,
                new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED), "Aman", EN);
    }

    private static Participant guest() {
        return Participant.guest(SESSION_ID, GuestParticipantToken.generate(), "Guest", KN);
    }

    @Test
    void registeredParticipantIsBackedByAnIdentity() {
        Participant participant = registered();

        assertThat(participant.getState()).isEqualTo(ParticipantState.JOINED);
        assertThat(participant.isGuest()).isFalse();
        assertThat(participant.getIdentityReference()).isNotNull();
        assertThat(participant.getGuestParticipantToken()).isNull();
        assertThat(participant.key().isGuest()).isFalse();
        assertThat(participant.isConnected()).isFalse();
    }

    @Test
    void guestParticipantIsBackedByAToken() {
        Participant participant = guest();

        assertThat(participant.isGuest()).isTrue();
        assertThat(participant.getGuestParticipantToken()).isNotNull();
        assertThat(participant.getIdentityReference()).isNull();
        assertThat(participant.key().isGuest()).isTrue();
        assertThat(participant.getPreferredLanguage()).isEqualTo(KN);
    }

    @Test
    void mustHaveExactlyOneIdentityMechanism() {
        // registered() with a null identity leaves neither mechanism present
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Participant.registered(SESSION_ID, null, "x", EN));
    }

    @Test
    void requiresDisplayNameAndLanguage() {
        assertThatIllegalArgumentException().isThrownBy(() -> Participant.registered(SESSION_ID,
                new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED), "  ", EN));
        assertThatNullPointerException().isThrownBy(() -> Participant.guest(SESSION_ID,
                GuestParticipantToken.generate(), "Guest", null));
    }

    @Test
    void connectivityIsDerivedFromState() {
        Participant participant = registered();

        participant.connect(T1);
        assertThat(participant.getState()).isEqualTo(ParticipantState.CONNECTED);
        assertThat(participant.isConnected()).isTrue();
        assertThat(participant.getLastSeenAt()).isEqualTo(T1);

        participant.disconnect(T2);
        assertThat(participant.getState()).isEqualTo(ParticipantState.DISCONNECTED);
        assertThat(participant.isConnected()).isFalse();
        assertThat(participant.getLastSeenAt()).isEqualTo(T2);
    }

    @Test
    void reconnectionPreservesScoreAndAnswers() {
        Participant participant = registered();
        participant.connect(T1);
        participant.recordAnswer(answer(600));
        participant.disconnect(T2);

        participant.connect(T2.plusSeconds(10));

        assertThat(participant.isConnected()).isTrue();
        assertThat(participant.getTotalScore()).isEqualTo(600);
        assertThat(participant.answers()).hasSize(1);
    }

    @Test
    void reconnectIsIdempotentWhileLive() {
        Participant participant = registered();
        participant.connect(T1);
        participant.recordAnswer(answer(600));

        participant.connect(T2);

        assertThat(participant.isConnected()).isTrue();
        assertThat(participant.getLastSeenAt()).isEqualTo(T2);
        assertThat(participant.getTotalScore()).isEqualTo(600);
        assertThat(participant.answers()).hasSize(1);
    }

    @Test
    void rejectsInvalidTransitions() {
        Participant participant = registered();

        assertThatExceptionOfType(InvalidParticipantTransitionException.class)
                .isThrownBy(() -> participant.disconnect(T1));

        participant.finish();
        assertThatExceptionOfType(InvalidParticipantTransitionException.class)
                .isThrownBy(() -> participant.connect(T1));
        assertThatExceptionOfType(InvalidParticipantTransitionException.class)
                .isThrownBy(participant::finish);
    }

    @Test
    void totalScoreIsTheCachedSumOfAnswerPoints() {
        Participant participant = registered();
        participant.connect(T1);

        participant.recordAnswer(answer(1000));
        participant.recordAnswer(answer(750));

        assertThat(participant.getTotalScore()).isEqualTo(1750);
        assertThat(participant.answers()).hasSize(2);
    }

    @Test
    void rejectsAnsweringTheSameQuestionTwice() {
        Participant participant = registered();
        UUID questionId = UUID.randomUUID();
        participant.recordAnswer(new ParticipantAnswer(questionId, Set.of(UUID.randomUUID()), EN, T1, 500, 100));

        assertThatIllegalArgumentException().isThrownBy(() -> participant.recordAnswer(
                new ParticipantAnswer(questionId, Set.of(UUID.randomUUID()), EN, T2, 400, 200)));
    }

    private static ParticipantAnswer answer(int points) {
        return new ParticipantAnswer(UUID.randomUUID(), Set.of(UUID.randomUUID()), EN, T1, 500, points);
    }
}
