package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.ParticipantRecoveryCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRecoveryCodeRepository extends JpaRepository<ParticipantRecoveryCode, UUID> {

    /**
     * The candidate for a redemption: the player types digits, and the PIN
     * they typed them against names the session. Scoped by session so a code
     * cannot be redeemed in a quiz it was not issued for — and so two
     * sessions can hold the same six digits without interfering.
     */
    Optional<ParticipantRecoveryCode> findBySessionIdAndCodeDigestValue(UUID sessionId, String digestValue);

    /**
     * Invalidates a participant's outstanding codes. Issuing a new one
     * supersedes the old: a host who clicks twice, or reads the first code
     * out wrongly, must not leave two live codes for the same player.
     */
    @Modifying
    @Query("update ParticipantRecoveryCode c set c.redeemedAt = :now "
            + "where c.participantId = :participantId and c.redeemedAt is null")
    int supersedeOutstanding(@Param("participantId") UUID participantId, @Param("now") Instant now);

    List<ParticipantRecoveryCode> findByParticipantId(UUID participantId);
}
