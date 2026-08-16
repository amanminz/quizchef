package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.Participant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findBySessionId(UUID sessionId);

    /** Guest reconnection: the guest token is globally unique. */
    Optional<Participant> findByGuestParticipantTokenValue(String guestParticipantTokenValue);

    /** Registered reconnection: at most one participant per identity per session. */
    Optional<Participant> findBySessionIdAndIdentityReferenceIdentityId(UUID sessionId, UUID identityId);

    /**
     * Which session a participant belongs to, without loading the
     * participant. Answer submission needs this before it may load
     * anything else: it has to take the session's write lock first (so a
     * correction or removal cannot reverse points alongside it), and a
     * participant loaded before that lock would be a snapshot from before
     * whatever the lock was protecting against.
     */
    @Query("select p.sessionId from Participant p where p.id = :participantId")
    Optional<UUID> findSessionIdById(@Param("participantId") UUID participantId);
}
