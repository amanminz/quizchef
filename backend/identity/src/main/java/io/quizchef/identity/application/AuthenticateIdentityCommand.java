package io.quizchef.identity.application;

/**
 * Intent to authenticate with email and password.
 *
 * @param userAgent optional client user agent, captured for the session
 * @param ipAddress optional client address, captured for the session
 */
public record AuthenticateIdentityCommand(
        String email,
        String password,
        String userAgent,
        String ipAddress
) {

    /**
     * The raw password must never reach logs, even accidentally.
     */
    @Override
    public String toString() {
        return "AuthenticateIdentityCommand[email=%s, password=<redacted>, userAgent=%s, ipAddress=%s]"
                .formatted(email, userAgent, ipAddress);
    }
}
