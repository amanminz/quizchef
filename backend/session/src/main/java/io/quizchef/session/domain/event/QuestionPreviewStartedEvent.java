package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question became current and its reading period began, ending at
 * {@code previewEndsAt} — options are withheld and no answer is accepted
 * until the server transitions to {@link QuestionStartedEvent}. Carries the
 * timer so clients can render the reading countdown — the server remains
 * the authority on when the reading period actually ends (ADR-006).
 */
public record QuestionPreviewStartedEvent(
        UUID sessionId,
        UUID questionId,
        Instant previewEndsAt,
        int previewDurationSeconds,
        Instant occurredAt
) implements DomainEvent {

    public QuestionPreviewStartedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(previewEndsAt, "previewEndsAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
