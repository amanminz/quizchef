package io.quizchef.identity.application;

import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The identity module's public answer to "is this login session still
 * valid?" — the security filter consults it for every bearer token, so a
 * revoked session invalidates its JWTs without any token blacklist.
 *
 * <p>Other modules never touch the identity repositories (architecture
 * constraint); this service is the boundary.
 */
@Service
public class IdentitySessionQueryService {

    private final IdentitySessionRepository identitySessionRepository;

    public IdentitySessionQueryService(IdentitySessionRepository identitySessionRepository) {
        this.identitySessionRepository = identitySessionRepository;
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId, UUID identityId) {
        return identitySessionRepository.findById(sessionId)
                .filter(IdentitySession::isActive)
                .filter(session -> session.getIdentityId().equals(identityId))
                .isPresent();
    }
}
