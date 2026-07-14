package io.quizchef.identity.domain;

/**
 * Domain port for password hashing.
 *
 * <p>The domain only ever handles hashes; the algorithm (Argon2id) is an
 * infrastructure concern behind this interface, so business code carries no
 * dependency on Spring Security.
 */
public interface PasswordHasher {

    String hash(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String passwordHash);
}
