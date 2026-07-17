package io.quizchef.session.application;

import java.util.UUID;

/**
 * The acknowledgement a participant receives for a submitted answer:
 * confirmation of receipt, and deliberately no score — points are the
 * server's to reveal, not to return here (ADR-006).
 */
public record AnswerAcceptedView(
        UUID participantId,
        UUID questionId
) {
}
