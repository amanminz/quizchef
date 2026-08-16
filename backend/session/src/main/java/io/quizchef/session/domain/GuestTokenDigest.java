package io.quizchef.session.domain;

import jakarta.persistence.Embeddable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The SHA-256 of a {@link GuestParticipantToken} — what the database
 * actually stores, and the only form of the credential that ever exists
 * outside the join response.
 *
 * <p>A resume token is a bearer credential: whoever holds it is that
 * participant, with their score and their answers. Storing the secret
 * itself would mean a database dump, a replica, a backup, or an
 * over-shared query result hands out live identities for every guest in
 * every session that has ever run. The digest cannot be replayed, and
 * because the token carries 256 bits of entropy there is no dictionary to
 * reverse it with — the usual reason password hashing needs a slow KDF and
 * a salt does not apply to a value the server itself generated at full
 * entropy.
 *
 * <p>Verification is by digest equality, which is also what makes the
 * lookup an ordinary indexed query. {@link #matches} still compares in
 * constant time: the cost is nil and it removes the question of whether
 * any future caller might compare these somewhere timing matters.
 */
@Embeddable
public record GuestTokenDigest(String value) {

    public GuestTokenDigest {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("guest token digest must not be blank");
        }
    }

    public static GuestTokenDigest of(GuestParticipantToken token) {
        Objects.requireNonNull(token, "token must not be null");
        return new GuestTokenDigest(sha256Hex(token.value()));
    }

    /** Rehydrates a digest already stored — never used on a raw token. */
    public static GuestTokenDigest ofStoredValue(String storedValue) {
        return new GuestTokenDigest(storedValue);
    }

    /**
     * Whether the presented token is the one this digest was made from.
     */
    public boolean matches(GuestParticipantToken presented) {
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
            // Every JVM is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
