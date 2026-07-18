package io.quizchef.identity.application;

import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The identity module's public answer to "is this login session still
 * valid, and what may its identity do right now?" — the security filter
 * consults it for every bearer token. A revoked session invalidates its
 * JWTs without any token blacklist; and because the answer carries the
 * identity's <em>persisted</em> roles (not the token's issuance-time
 * claim), a role granted mid-session takes effect on the very next
 * request — no re-login, no token swap. A disabled identity is refused
 * here too, closing the gap where an existing session outlived a
 * deactivation.
 *
 * <p>Other modules never touch the identity repositories (architecture
 * constraint); this service is the boundary.
 */
@Service
public class IdentitySessionQueryService {

    private final IdentitySessionRepository identitySessionRepository;
    private final IdentityRepository identityRepository;

    public IdentitySessionQueryService(IdentitySessionRepository identitySessionRepository,
                                       IdentityRepository identityRepository) {
        this.identitySessionRepository = identitySessionRepository;
        this.identityRepository = identityRepository;
    }

    /**
     * The identity's current roles, if — and only if — the session is
     * active, belongs to that identity, and the identity itself is still
     * active. Empty means the bearer token must be rejected.
     */
    @Transactional(readOnly = true)
    public Optional<Set<Role>> activeSessionRoles(UUID sessionId, UUID identityId) {
        boolean sessionValid = identitySessionRepository.findById(sessionId)
                .filter(IdentitySession::isActive)
                .filter(session -> session.getIdentityId().equals(identityId))
                .isPresent();
        if (!sessionValid) {
            return Optional.empty();
        }
        return identityRepository.findById(identityId)
                .filter(Identity::isActive)
                .map(Identity::roles);
    }
}
