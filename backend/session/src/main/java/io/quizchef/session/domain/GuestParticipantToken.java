package io.quizchef.session.domain;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The opaque secret a guest stores client-side and presents to resume
 * (ADR-003). It is a resume credential, never a business identity —
 * nothing in the domain decides who someone <em>is</em> from this token,
 * only whether they may rebind to an existing participant.
 *
 * <p><strong>Deliberately not persistable.</strong> It has no JPA mapping
 * and never reaches the database: what is stored is its {@link
 * GuestTokenDigest}. The raw secret exists in exactly two places — the
 * join response that issues it, and the resume request that presents it —
 * so it is never logged, never broadcast, and never readable back out of
 * any API or table.
 */
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

    /** What the server stores in place of this secret. */
    public GuestTokenDigest digest() {
        return GuestTokenDigest.of(this);
    }

    /**
     * Never renders the secret. A token that reaches a log line by accident
     * — through a command object, an exception message, or a debugger — is
     * a leaked credential, so the only safe {@code toString} is one that
     * has nothing to leak.
     */
    @Override
    public String toString() {
        return "GuestParticipantToken[REDACTED]";
    }
}
