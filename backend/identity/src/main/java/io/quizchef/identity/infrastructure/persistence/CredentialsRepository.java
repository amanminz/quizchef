package io.quizchef.identity.infrastructure.persistence;

import io.quizchef.identity.domain.Credentials;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialsRepository extends JpaRepository<Credentials, UUID> {

    Optional<Credentials> findByIdentityId(UUID identityId);
}
