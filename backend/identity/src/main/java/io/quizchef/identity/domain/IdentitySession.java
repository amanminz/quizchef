package io.quizchef.identity.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An authenticated login session of an identity.
 *
 * <p>Durable by design (ADR-003 / ADR-004): it describes that an identity is
 * logged in, never how its bytes travel. Client details are informational
 * and nullable — not every client reveals them.
 *
 * <p>{@code lastSeenAt} moves on every interaction; {@code lastAuthenticatedAt}
 * only when the identity actually proves itself (login, token refresh).
 */
@Entity
@Table(name = "identity_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentitySession extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID identityId;

    @Column(length = 255)
    private String refreshTokenHash;

    @Column(length = 255)
    private String deviceFingerprint;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private Instant lastAuthenticatedAt;

    @Column(nullable = false)
    private boolean revoked;

    private IdentitySession(UUID id, UUID identityId, String userAgent, String ipAddress,
                            String deviceFingerprint) {
        super(id);
        this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.deviceFingerprint = deviceFingerprint;
        Instant now = Instant.now();
        this.lastSeenAt = now;
        this.lastAuthenticatedAt = now;
        this.revoked = false;
    }

    public static IdentitySession start(UUID identityId, String userAgent, String ipAddress,
                                        String deviceFingerprint) {
        return new IdentitySession(UUID.randomUUID(), identityId, userAgent, ipAddress, deviceFingerprint);
    }

    public void attachRefreshToken(String refreshTokenHash) {
        requireActive();
        this.refreshTokenHash = refreshTokenHash;
    }

    public void touch() {
        requireActive();
        this.lastSeenAt = Instant.now();
    }

    public void revoke() {
        this.revoked = true;
        this.refreshTokenHash = null;
    }

    public boolean isActive() {
        return !revoked;
    }

    private void requireActive() {
        if (revoked) {
            throw new IllegalStateException("Identity session has been revoked");
        }
    }
}
