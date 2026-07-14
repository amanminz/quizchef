package io.quizchef.identity.infrastructure.persistence;

import io.quizchef.identity.domain.Identity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRepository extends JpaRepository<Identity, UUID> {
}
