package io.quizchef.session.application;

/**
 * A player's claim to be someone already in this session.
 *
 * @param sessionPin  the code the player actually holds; resolves to the
 *                    session that is live under it right now
 * @param resumeToken the secret issued at join, for a guest; null for a
 *                    registered player, who is resolved from their bearer
 *                    identity instead
 */
public record ResumeParticipantCommand(
        String sessionPin,
        String resumeToken
) {
}
