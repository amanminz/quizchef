package io.quizchef.identity.infrastructure.persistence;

import io.quizchef.identity.domain.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByIdentityId(UUID identityId);

    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);
}
