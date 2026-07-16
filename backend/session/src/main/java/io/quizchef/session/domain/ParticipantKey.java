package io.quizchef.session.domain;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.UUID;

/**
 * How a session recognizes one participant: a registered identity or a
 * guest token — exactly one. It carries only the participant's
 * <em>immutable</em> identity, never mutable state (name, score,
 * connection), so the Session roster can hold it without ever diverging
 * from the Participant aggregate.
 *
 * <p>Value equality is the point: the Session compares keys to enforce
 * "one identity per session" and "one guest token per session" in-memory.
 * Stored flattened (not as nested embeddables) so it sits one level deep
 * inside the roster element collection.
 */
@Embeddable
public record ParticipantKey(
        @Column(name = "identity_id") UUID identityId,
        @Enumerated(EnumType.STRING) @Column(name = "identity_type", length = 20) IdentityType identityType,
        @Column(name = "guest_token") String guestToken
) {

    public ParticipantKey {
        boolean hasIdentity = identityId != null;
        boolean hasGuest = guestToken != null && !guestToken.isBlank();
        if (hasIdentity == hasGuest) {
            throw new IllegalArgumentException(
                    "a participant key must be exactly one of an identity or a guest token");
        }
        if (hasIdentity) {
            Objects.requireNonNull(identityType, "identityType is required for an identity key");
        }
    }

    public static ParticipantKey forIdentity(IdentityReference reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        return new ParticipantKey(reference.identityId(), reference.identityType(), null);
    }

    public static ParticipantKey forGuest(GuestParticipantToken token) {
        Objects.requireNonNull(token, "token must not be null");
        return new ParticipantKey(null, null, token.value());
    }

    public boolean isGuest() {
        return guestToken != null;
    }
}
