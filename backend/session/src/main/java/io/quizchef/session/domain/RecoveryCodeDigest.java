package io.quizchef.session.domain;

import jakarta.persistence.Embeddable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The SHA-256 of a {@link RecoveryCode} — what is stored, so a database
 * leak does not hand over codes that are still live.
 *
 * <p>Unlike {@link GuestTokenDigest} this one hashes a genuinely small
 * space: six digits is a million possibilities, which is nothing to an
 * offline attacker holding the table. That is a deliberate, bounded
 * acceptance rather than an oversight — a code expires in minutes, works
 * once, and names one participant in one session, so a digest recovered
 * offline is almost certainly already dead. What actually stops guessing is
 * the rate limit on the redemption endpoint, not this.
 */
@Embeddable
public record RecoveryCodeDigest(String value) {

    public RecoveryCodeDigest {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("recovery code digest must not be blank");
        }
    }

    public static RecoveryCodeDigest of(RecoveryCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return new RecoveryCodeDigest(sha256Hex(code.value()));
    }

    public boolean matches(RecoveryCode presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8),
                sha256Hex(presented.value()).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
