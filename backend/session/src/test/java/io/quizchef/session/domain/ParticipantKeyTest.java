package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantKeyTest {

    @Test
    void identityKeysAreEqualForTheSameIdentity() {
        UUID id = UUID.randomUUID();
        ParticipantKey first = ParticipantKey.forIdentity(new IdentityReference(id, IdentityType.REGISTERED));
        ParticipantKey second = ParticipantKey.forIdentity(new IdentityReference(id, IdentityType.REGISTERED));

        assertThat(first).isEqualTo(second);
        assertThat(first.isGuest()).isFalse();
    }

    @Test
    void guestKeysAreEqualForTheSameToken() {
        GuestParticipantToken token = GuestParticipantToken.of("token-123");
        ParticipantKey first = ParticipantKey.forGuest(token);
        ParticipantKey second = ParticipantKey.forGuest(GuestParticipantToken.of("token-123"));

        assertThat(first).isEqualTo(second);
        assertThat(first.isGuest()).isTrue();
    }

    @Test
    void differentIdentitiesAndTokensAreDistinct() {
        ParticipantKey identity = ParticipantKey.forIdentity(
                new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED));
        ParticipantKey guest = ParticipantKey.forGuest(GuestParticipantToken.of("token-123"));

        assertThat(identity).isNotEqualTo(guest);
        assertThat(ParticipantKey.forGuest(GuestParticipantToken.of("a")))
                .isNotEqualTo(ParticipantKey.forGuest(GuestParticipantToken.of("b")));
    }

    @Test
    void mustBeExactlyOneOfIdentityOrGuest() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParticipantKey(null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParticipantKey(UUID.randomUUID(), IdentityType.REGISTERED, "token"));
    }
}
