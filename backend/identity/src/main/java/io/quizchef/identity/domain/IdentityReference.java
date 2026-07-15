package io.quizchef.identity.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.UUID;

/**
 * The way other modules refer to an identity.
 *
 * <p>A value object carrying only who is acting — never credentials,
 * profile, or sessions. Aggregates in other modules (Quiz owner,
 * Participant) embed an IdentityReference instead of reaching into the
 * identity aggregates.
 */
@Embeddable
public record IdentityReference(
        UUID identityId,
        @Enumerated(EnumType.STRING) IdentityType identityType) {

    public IdentityReference {
        Objects.requireNonNull(identityId, "identityId must not be null");
        Objects.requireNonNull(identityType, "identityType must not be null");
    }

    public boolean isGuest() {
        return identityType == IdentityType.GUEST;
    }
}
