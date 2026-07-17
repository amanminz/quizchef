package io.quizchef.websocket.api.event;

import io.quizchef.websocket.api.ProtocolPayload;
import java.util.Set;
import java.util.UUID;

/**
 * The body of {@code answer.revealed}: the correct option ids for the
 * question, so clients can highlight them. The first moment correctness
 * crosses the wire (ADR-006).
 */
public record AnswerRevealedPayload(
        UUID questionId,
        Set<UUID> correctOptionIds
) implements ProtocolPayload {
}
