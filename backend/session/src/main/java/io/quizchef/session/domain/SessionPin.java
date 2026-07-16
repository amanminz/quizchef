package io.quizchef.session.domain;

import jakarta.persistence.Embeddable;

/**
 * The six-digit code participants type to join a session.
 *
 * <p>The value object only guarantees the shape (exactly six digits).
 * Uniqueness among <em>active</em> sessions — and reuse once a session is
 * archived — is a database concern (a partial unique index) enforced when
 * the session is created, because no single aggregate can see the others.
 */
@Embeddable
public record SessionPin(String value) {

    private static final int LENGTH = 6;

    public SessionPin {
        if (value == null || !value.matches("\\d{" + LENGTH + "}")) {
            throw new IllegalArgumentException("session pin must be exactly %d digits".formatted(LENGTH));
        }
    }

    public static SessionPin of(String value) {
        return new SessionPin(value);
    }
}
