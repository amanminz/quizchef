package io.quizchef.session.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionApiValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void joinRequestRejectsAnOverlongLanguageTag() {
        JoinSessionRequest request = new JoinSessionRequest("Aman", "x".repeat(36));

        assertThat(validator.validate(request))
                .anyMatch(v -> v.getPropertyPath().toString().equals("preferredLanguage"));
    }

    @Test
    void joinRequestAcceptsALanguageTagAtTheCeiling() {
        JoinSessionRequest request = new JoinSessionRequest("Aman", "x".repeat(35));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void reconnectRequestAcceptsExactlyOneIdentifier() {
        assertThat(validator.validate(new ReconnectRequest(UUID.randomUUID(), null))).isEmpty();
        assertThat(validator.validate(new ReconnectRequest(null, "guest-token"))).isEmpty();
    }

    @Test
    void reconnectRequestRejectsNeitherIdentifier() {
        assertThat(validator.validate(new ReconnectRequest(null, null)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("exactlyOneIdentifierPresent"));
    }

    @Test
    void reconnectRequestRejectsBothIdentifiers() {
        assertThat(validator.validate(new ReconnectRequest(UUID.randomUUID(), "guest-token")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("exactlyOneIdentifierPresent"));
    }

    @Test
    void reconnectRequestRejectsAnOverlongGuestToken() {
        assertThat(validator.validate(new ReconnectRequest(null, "x".repeat(129))))
                .anyMatch(v -> v.getPropertyPath().toString().equals("guestParticipantToken"));
    }

    @Test
    void submitAnswerRequestRejectsTooManySelectedOptions() {
        Set<UUID> tooMany = new java.util.HashSet<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add(UUID.randomUUID());
        }
        SubmitAnswerRequest request = new SubmitAnswerRequest(UUID.randomUUID(), UUID.randomUUID(), tooMany);

        assertThat(validator.validate(request))
                .anyMatch(v -> v.getPropertyPath().toString().equals("selectedOptionIds"));
    }
}
