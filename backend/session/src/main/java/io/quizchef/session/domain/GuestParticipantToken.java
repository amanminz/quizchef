package io.quizchef.session.domain;

import jakarta.persistence.Embeddable;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The opaque secret a guest stores client-side and presents to reconnect
 * (ADR-003). It is a reconnection credential, never a business identity —
 * nothing in the domain decides who someone <em>is</em> from this token,
 * only whether they may rebind to an existing participant.
 */
@Embeddable
public record GuestParticipantToken(String value) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    public GuestParticipantToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("guest participant token must not be blank");
        }
    }

    /**
     * A fresh, cryptographically random token (URL-safe, no padding).
     */
    public static GuestParticipantToken generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new GuestParticipantToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static GuestParticipantToken of(String value) {
        return new GuestParticipantToken(value);
    }
}
