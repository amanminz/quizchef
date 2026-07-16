package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.Session;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {
}
