package io.quizchef.session.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A short-lived code a host reads out so one specific player can get back
 * into the game from a device that has lost its resume credential.
 *
 * <p>The last resort, and deliberately the only one. A player whose browser
 * storage is gone can prove nothing by themselves — that is the whole point
 * of the resume token — so the authority has to come from somewhere else,
 * and the host physically looking at the person in the room is a better
 * authority than any secret they could type.
 *
 * <p>Six digits is a small space, so the hash is not what protects this.
 * The protection is that a code lives about five minutes, works once, is
 * bound to one participant in one session, and is rate limited on redemption
 * — a guessing attack has a handful of tries against a target that expires
 * while it works. The digest exists so a leaked database does not hand over
 * codes that are still live, not because the space is large.
 */
@Entity
@Table(name = "participant_recovery_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipantRecoveryCode extends AuditableEntity {

    /** How long a code stays usable. Long enough to read out, short enough to forget. */
    public static final Duration LIFETIME = Duration.ofMinutes(5);

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "code_digest", nullable = false, updatable = false))
    private RecoveryCodeDigest codeDigest;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Set the moment it is used. A code is never usable twice. */
    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    private ParticipantRecoveryCode(UUID id, UUID sessionId, UUID participantId,
                                    RecoveryCodeDigest codeDigest, Instant expiresAt) {
        super(id);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.participantId = Objects.requireNonNull(participantId, "participantId must not be null");
        this.codeDigest = Objects.requireNonNull(codeDigest, "codeDigest must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static ParticipantRecoveryCode issue(UUID sessionId, UUID participantId,
                                                RecoveryCode code, Instant now) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new ParticipantRecoveryCode(UUID.randomUUID(), sessionId, participantId,
                RecoveryCodeDigest.of(code), now.plus(LIFETIME));
    }

    public boolean isRedeemable(Instant now) {
        return redeemedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Marks this code used. Returns false if it already was, or if it has
     * expired — so a second redemption of the same code cannot succeed even
     * if two requests arrive together.
     */
    public boolean redeem(Instant now) {
        if (!isRedeemable(now)) {
            return false;
        }
        this.redeemedAt = now;
        return true;
    }

    public boolean matches(RecoveryCode presented) {
        return codeDigest.matches(presented);
    }
}
