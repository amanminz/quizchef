package io.quizchef.identity.infrastructure.password;

import io.quizchef.identity.domain.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id implementation of the {@link PasswordHasher} port.
 *
 * <p>Argon2 was selected over BCrypt because it is memory-hard: cracking
 * attempts on GPUs and ASICs are bounded by memory bandwidth, not just
 * compute. It won the Password Hashing Competition and is the current OWASP
 * first-choice recommendation. Parameters follow Spring Security's 5.8+
 * defaults (Argon2id, 16 MiB memory, 2 iterations, parallelism 1).
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder =
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String hash(CharSequence rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
