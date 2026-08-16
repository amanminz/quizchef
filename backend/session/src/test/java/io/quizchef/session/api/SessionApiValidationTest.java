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
    void resumeRequestRejectsAnOverlongToken() {
        assertThat(validator.validate(new ResumeParticipantRequest("x".repeat(129))))
                .anyMatch(v -> v.getPropertyPath().toString().equals("resumeToken"));
    }

    @Test
    void resumeRequestAcceptsAnAbsentTokenForASignedInPlayer() {
        // A registered player resumes on their bearer identity and has no
        // token at all; the service, not this shape, decides whether they
        // are actually signed in.
        assertThat(validator.validate(new ResumeParticipantRequest(null))).isEmpty();
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

    @Test
    void correctionRejectsAnEmptyAnswerKey() {
        // A corrected question with nothing marked correct cannot be scored
        // at all — worse than the wrong key it was meant to fix.
        CorrectQuestionRequest request = new CorrectQuestionRequest(Set.of(),
                java.util.List.of(new CorrectQuestionRequest.CorrectedLocalizationDto(
                        "en", "Prompt", java.util.List.of())));

        assertThat(validator.validate(request))
                .anyMatch(v -> v.getPropertyPath().toString().equals("correctOptionIds"));
    }

    @Test
    void correctionRejectsACorrectionThatSaysNothingInAnyLanguage() {
        CorrectQuestionRequest request =
                new CorrectQuestionRequest(Set.of(UUID.randomUUID()), java.util.List.of());

        assertThat(validator.validate(request))
                .anyMatch(v -> v.getPropertyPath().toString().equals("localizations"));
    }

    @Test
    void correctionRejectsABlankPrompt() {
        CorrectQuestionRequest request = new CorrectQuestionRequest(Set.of(UUID.randomUUID()),
                java.util.List.of(new CorrectQuestionRequest.CorrectedLocalizationDto(
                        "en", "   ", java.util.List.of())));

        assertThat(validator.validate(request))
                .anyMatch(v -> v.getPropertyPath().toString().startsWith("localizations[0].prompt"));
    }

    @Test
    void correctionAcceptsAFixInOneLanguageOnly() {
        // Languages omitted keep their authored text, so a host fixing only
        // the English is a complete, valid correction.
        CorrectQuestionRequest request = new CorrectQuestionRequest(Set.of(UUID.randomUUID()),
                java.util.List.of(new CorrectQuestionRequest.CorrectedLocalizationDto(
                        "en", "The corrected prompt",
                        java.util.List.of(new CorrectQuestionRequest.CorrectedOptionDto(
                                UUID.randomUUID(), "Corrected option")))));

        assertThat(validator.validate(request)).isEmpty();
    }
}
