package io.quizchef.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question the host pulled out of one running session, and the record of
 * pulling it.
 *
 * <p>Lives inside the {@link Session} aggregate because removal changes the
 * sequence the session plays, and that sequence is the session's — the
 * published quiz is untouched, so a second session of the same quiz still
 * asks the question this one dropped.
 *
 * <p>It is deliberately a marker rather than a deletion. The question
 * vanishes from the effective sequence (numbering, progression, scoring,
 * history all read past it), but the fact that a host removed it, from
 * which phase, and how many answers that cancelled survives in the
 * session's own record — the cheapest form of the audit entry a completed
 * session's history is worth keeping.
 */
@Embeddable
public record RemovedQuestion(
        @Column(name = "question_id", nullable = false)
        UUID questionId,
        @Column(name = "removed_at", nullable = false)
        Instant removedAt,
        /** The phase the question was in when pulled; null if it had not been reached yet. */
        @Enumerated(EnumType.STRING)
        @Column(name = "removed_from_phase", length = 20)
        SessionPhase removedFromPhase,
        /** How many accepted answers this removal cancelled. Zero for an upcoming question. */
        @Column(name = "cancelled_answer_count", nullable = false)
        int cancelledAnswerCount
) {

    public RemovedQuestion {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(removedAt, "removedAt must not be null");
        if (cancelledAnswerCount < 0) {
            throw new IllegalArgumentException("cancelledAnswerCount must not be negative");
        }
    }
}
