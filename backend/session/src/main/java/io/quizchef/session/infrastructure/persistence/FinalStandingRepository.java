package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.FinalStanding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalStandingRepository extends JpaRepository<FinalStanding, UUID> {

    /** The session's finishing order, as captured — never re-ranked. */
    List<FinalStanding> findBySessionIdOrderByFinalRankAsc(UUID sessionId);

    boolean existsBySessionId(UUID sessionId);
}
