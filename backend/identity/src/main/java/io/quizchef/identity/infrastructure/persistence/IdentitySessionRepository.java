package io.quizchef.identity.infrastructure.persistence;

import io.quizchef.identity.domain.IdentitySession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySessionRepository extends JpaRepository<IdentitySession, UUID> {

    List<IdentitySession> findByIdentityIdAndRevokedFalse(UUID identityId);
}
