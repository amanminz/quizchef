package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The host corrected a question inside a running session — its wording, or
 * which options are correct, or both.
 *
 * <p>Carries ids and a timestamp only. What changed is deliberately absent:
 * the corrected content includes the answer key, and an event is broadcast
 * to every device in the room. Clients re-read the authoritative session
 * and question state, which is already phase-gated to hide correctness
 * until the reveal.
 *
 * <p>When the corrected question was the one in play, a {@link
 * QuestionPreviewStartedEvent} follows it: the attempt was cancelled and the
 * fixed question re-enters its reading period.
 */
public record QuestionCorrectedEvent(
        UUID sessionId,
        UUID questionId,
        /** Whether the correction cancelled the attempt in progress and replayed it. */
        boolean replayed,
        Instant occurredAt
) implements DomainEvent {

    public QuestionCorrectedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
