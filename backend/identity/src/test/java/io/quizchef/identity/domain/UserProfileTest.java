package io.quizchef.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProfileTest {

    @Test
    void shouldNormalizeEmailToLowerCase() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "Aman", "  Aman@Example.COM ");

        assertThat(profile.getEmail()).isEqualTo("aman@example.com");
    }

    @Test
    void shouldNormalizeEmailOnChange() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "Aman", "aman@example.com");

        profile.changeEmail("NEW@Example.com");

        assertThat(profile.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void shouldRejectBlankDisplayName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserProfile.create(UUID.randomUUID(), "   ", "aman@example.com"));
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserProfile.create(UUID.randomUUID(), "Aman", " "));
    }
}
