package io.quizchef.identity.domain.exception;

import io.quizchef.common.exception.ResourceNotFoundException;
import java.util.UUID;

/**
 * No identity exists for the requested id.
 */
public class IdentityNotFoundException extends ResourceNotFoundException {

    public IdentityNotFoundException(UUID identityId) {
        super("identity.not-found", "Identity %s does not exist".formatted(identityId));
    }
}
