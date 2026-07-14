package io.quizchef.identity.infrastructure.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    @Test
    void shouldProduceArgon2idHashThatNeverContainsRawPassword() {
        String hash = hasher.hash("correct horse battery staple");

        assertThat(hash).startsWith("$argon2id$");
        assertThat(hash).doesNotContain("correct horse battery staple");
    }

    @Test
    void shouldMatchCorrectPassword() {
        String hash = hasher.hash("s3cret-password");

        assertThat(hasher.matches("s3cret-password", hash)).isTrue();
    }

    @Test
    void shouldRejectWrongPassword() {
        String hash = hasher.hash("s3cret-password");

        assertThat(hasher.matches("s3cret-passw0rd", hash)).isFalse();
    }

    @Test
    void shouldSaltHashesSoEqualPasswordsProduceDifferentHashes() {
        assertThat(hasher.hash("same-password")).isNotEqualTo(hasher.hash("same-password"));
    }
}
