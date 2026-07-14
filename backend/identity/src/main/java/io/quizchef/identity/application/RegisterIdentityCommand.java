package io.quizchef.identity.application;

/**
 * Intent to register a new identity.
 *
 * @param phoneNumber optional; null or blank means not provided
 */
public record RegisterIdentityCommand(
        String displayName,
        String email,
        String password,
        String phoneNumber
) {

    /**
     * The raw password must never reach logs, even accidentally.
     */
    @Override
    public String toString() {
        return "RegisterIdentityCommand[displayName=%s, email=%s, password=<redacted>, phoneNumber=%s]"
                .formatted(displayName, email, phoneNumber);
    }
}
