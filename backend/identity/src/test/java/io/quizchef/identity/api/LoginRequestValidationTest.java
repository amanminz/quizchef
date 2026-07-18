package io.quizchef.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoginRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAPasswordAtTheLengthCeiling() {
        LoginRequest request = new LoginRequest("aman@example.com", "a".repeat(128));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsAPasswordBeyondTheLengthCeiling() {
        LoginRequest request = new LoginRequest("aman@example.com", "a".repeat(129));

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
