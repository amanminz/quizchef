package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.Participant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findBySessionId(UUID sessionId);
}
