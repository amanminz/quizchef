package io.quizchef.session.domain.event;

import io.quizchef.common.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The host pulled a question out of a running session. Its answers are
 * cancelled, its points reversed, and the session's numbering already
 * reflects a sequence that no longer contains it.
 *
 * <p>Carries ids and a timestamp only — no correct answer, no score, no
 * standings. A removed question's answer is precisely what must not be
 * shown: the room never got to finish it, and revealing it would tell them
 * what they were about to be asked. Clients re-read authoritative state.
 *
 * <p>Followed by a {@link QuestionPreviewStartedEvent} for whatever comes
 * next, or by a {@link SessionFinishedEvent} when the removed question was
 * the last one left to play.
 */
public record QuestionRemovedEvent(
        UUID sessionId,
        UUID questionId,
        /** Whether this removed the question in play, rather than an upcoming one. */
        boolean wasInPlay,
        Instant occurredAt
) implements DomainEvent {

    public QuestionRemovedEvent {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
