package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.Participant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findBySessionId(UUID sessionId);

    /** Guest reconnection: the guest token is globally unique. */
    Optional<Participant> findByGuestParticipantTokenValue(String guestParticipantTokenValue);

    /** Registered reconnection: at most one participant per identity per session. */
    Optional<Participant> findBySessionIdAndIdentityReferenceIdentityId(UUID sessionId, UUID identityId);
}
