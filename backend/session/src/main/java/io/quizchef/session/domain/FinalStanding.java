package io.quizchef.session.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One participant's finishing position, as it stood the moment the session
 * ended.
 *
 * <p>A record of an event, not a projection of current state. The host's
 * live standings are computed from participants' rows and ranked at read
 * time (ADR-006) — correct while a game is running, wrong afterwards: a
 * later change to the ranking rule would rewrite the result of an event
 * that already happened, and a display name edited next month would
 * retroactively rename someone in a past quiz. So the name, the rank, and
 * the score are all copied in at completion and never touched again.
 *
 * <p>There is deliberately no setter and no update path. The only way this
 * row changes is if the session it belongs to is deleted.
 */
@Entity
@Table(name = "final_standings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalStanding extends AuditableEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    /**
     * What they were called at the time. Copied, not referenced — the point
     * of the snapshot is that it cannot be changed from elsewhere.
     */
    @Column(name = "display_name_at_completion", nullable = false, updatable = false, length = 100)
    private String displayNameAtCompletion;

    @Column(name = "final_rank", nullable = false, updatable = false)
    private int finalRank;

    @Column(name = "final_score", nullable = false, updatable = false)
    private int finalScore;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    private FinalStanding(UUID sessionId, LeaderboardEntry entry, Instant capturedAt) {
        super(UUID.randomUUID());
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        this.participantId = entry.participantId();
        this.displayNameAtCompletion = entry.displayName();
        this.finalRank = entry.rank();
        this.finalScore = entry.score();
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    /**
     * Captures one ranked row exactly as the ranking service produced it —
     * including the rank, so a future ranking rule cannot renumber it.
     */
    public static FinalStanding capture(UUID sessionId, LeaderboardEntry entry, Instant at) {
        return new FinalStanding(sessionId, entry, at);
    }
}
