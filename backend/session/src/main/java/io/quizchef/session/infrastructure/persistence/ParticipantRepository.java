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

    /**
     * Guest resume, scoped to one session. Looked up by digest — the raw
     * token is never stored, so this is the only shape the query can take —
     * and by session, so a token issued for one quiz cannot resolve inside
     * another.
     */
    Optional<Participant> findBySessionIdAndGuestTokenDigestValue(UUID sessionId, String digestValue);

    /**
     * Whether this session already has someone playing under that name.
     * Case-insensitive: "aman" and "Aman" are the same person to a room.
     */
    boolean existsBySessionIdAndDisplayNameIgnoreCase(UUID sessionId, String displayName);

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
