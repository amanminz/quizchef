package io.quizchef.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The way other modules refer to an identity.
 *
 * <p>A value object carrying only who is acting — never credentials,
 * profile, or sessions. Future aggregates such as Participant hold an
 * IdentityReference instead of reaching into the identity aggregates.
 */
public record IdentityReference(UUID identityId, IdentityType identityType) {

    public IdentityReference {
        Objects.requireNonNull(identityId, "identityId must not be null");
        Objects.requireNonNull(identityType, "identityType must not be null");
    }

    public boolean isGuest() {
        return identityType == IdentityType.GUEST;
    }
}
