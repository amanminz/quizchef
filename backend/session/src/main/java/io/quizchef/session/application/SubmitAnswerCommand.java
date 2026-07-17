package io.quizchef.session.application;

import java.util.Set;
import java.util.UUID;

/**
 * A participant's answer to the open question. The participant is named by
 * the id they were given on join; the server stamps the response time and
 * decides everything else.
 */
public record SubmitAnswerCommand(
        UUID participantId,
        UUID questionId,
        Set<UUID> selectedOptionIds
) {
}
