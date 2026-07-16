package io.quizchef.session.application;

/**
 * Joins a session by PIN. A display name and preferred language are always
 * supplied (both guests and registered players pick a per-session
 * nickname); the identity, if any, comes from the authenticated caller, not
 * the command.
 */
public record JoinSessionCommand(
        String sessionPin,
        String displayName,
        String preferredLanguage
) {
}
