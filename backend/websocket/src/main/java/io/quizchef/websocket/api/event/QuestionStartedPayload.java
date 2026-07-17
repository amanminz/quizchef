package io.quizchef.websocket.api.event;

import io.quizchef.websocket.api.ProtocolPayload;
import java.time.Instant;
import java.util.UUID;

/**
 * The body of {@code question.started}: which question opened and when it
 * closes, so clients can render the countdown. The server remains the
 * authority on the actual close (ADR-006).
 */
public record QuestionStartedPayload(
        UUID questionId,
        Instant endsAt,
        int durationSeconds
) implements ProtocolPayload {
}
