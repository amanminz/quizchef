package io.quizchef.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentitySessionTest {

    @Test
    void shouldStartActiveWithSeenAndAuthenticatedTimestamps() {
        IdentitySession session = IdentitySession.start(UUID.randomUUID(), "JUnit", "127.0.0.1", null);

        assertThat(session.isActive()).isTrue();
        assertThat(session.isRevoked()).isFalse();
        assertThat(session.getLastSeenAt()).isNotNull();
        assertThat(session.getLastAuthenticatedAt()).isNotNull();
    }

    @Test
    void shouldNotMoveAuthenticationTimestampOnTouch() {
        IdentitySession session = IdentitySession.start(UUID.randomUUID(), "JUnit", "127.0.0.1", null);
        var authenticatedAt = session.getLastAuthenticatedAt();

        session.touch();

        assertThat(session.getLastAuthenticatedAt()).isEqualTo(authenticatedAt);
        assertThat(session.getLastSeenAt()).isAfterOrEqualTo(authenticatedAt);
    }

    @Test
    void shouldClearRefreshTokenOnRevocation() {
        IdentitySession session = IdentitySession.start(UUID.randomUUID(), "JUnit", "127.0.0.1", null);
        session.attachRefreshToken("refresh-token-hash");

        session.revoke();

        assertThat(session.isActive()).isFalse();
        assertThat(session.getRefreshTokenHash()).isNull();
    }

    @Test
    void shouldRejectTouchingRevokedSession() {
        IdentitySession session = IdentitySession.start(UUID.randomUUID(), "JUnit", "127.0.0.1", null);
        session.revoke();

        assertThatIllegalStateException().isThrownBy(session::touch);
    }
}
