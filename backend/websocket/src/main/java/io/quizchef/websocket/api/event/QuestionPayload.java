package io.quizchef.websocket.api.event;

import io.quizchef.websocket.api.ProtocolPayload;
import java.util.UUID;

/**
 * The body of messages that name a question without extra data — for example
 * {@code question.closed} and {@code participant.answer.accepted}.
 */
public record QuestionPayload(UUID questionId) implements ProtocolPayload {
}
