package io.quizchef.session.domain;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * The six digits a host reads out to a stranded player.
 *
 * <p>Short because it is spoken across a room and typed on a phone by
 * someone already frustrated. Everything that makes it safe is around it
 * rather than in it: minutes of life, one use, one participant, one
 * session, and a rate limit on redemption (see
 * {@link ParticipantRecoveryCode}).
 *
 * <p>Not persistable, exactly like {@link GuestParticipantToken}: the
 * database stores a {@link RecoveryCodeDigest}, and the digits themselves
 * exist only in the host's response and the player's request.
 */
public record RecoveryCode(String value) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BOUND = 1_000_000;

    public RecoveryCode {
        if (value == null || !value.matches("\\d{6}")) {
            throw new IllegalArgumentException("a recovery code is six digits");
        }
    }

    /** Uniformly random over all six-digit strings, leading zeros included. */
    public static RecoveryCode generate() {
        return new RecoveryCode("%06d".formatted(RANDOM.nextInt(BOUND)));
    }

    public static RecoveryCode of(String value) {
        return new RecoveryCode(Objects.requireNonNull(value, "value must not be null").strip());
    }

    /** Never renders the code — it is a credential for the minutes it lives. */
    @Override
    public String toString() {
        return "RecoveryCode[REDACTED]";
    }
}
