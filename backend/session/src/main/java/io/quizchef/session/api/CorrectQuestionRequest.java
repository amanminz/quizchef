package io.quizchef.session.api;

import io.quizchef.session.application.CorrectQuestionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A question as the host says it should now read, for this session only.
 *
 * <p>States the corrected question rather than a diff — the host is looking
 * at it and submitting the fixed version. Languages omitted keep their
 * authored text, so correcting the English does not blank the Hindi.
 *
 * <p>Options may be reworded and re-marked but never added or dropped:
 * every {@code optionId} must be one the question already has. Answers
 * already recorded point at those ids, and a changed option set would leave
 * the cancelled attempt and the replayed one incomparable.
 */
public record CorrectQuestionRequest(
        @Schema(description = "Which options the corrected question treats as correct; replaces the "
                + "authored answer key outright")
        @NotEmpty @Size(max = 20) Set<UUID> correctOptionIds,
        @Schema(description = "The corrected wording, per language. Only the languages present change.")
        @NotEmpty @Size(max = 20) @Valid List<CorrectedLocalizationDto> localizations
) {

    public record CorrectedLocalizationDto(
            @Schema(example = "en") @NotBlank @Size(max = 20) String languageCode,
            @NotBlank @Size(max = 2000) String prompt,
            @Valid @Size(max = 20) List<CorrectedOptionDto> options
    ) {
    }

    public record CorrectedOptionDto(
            @Schema(description = "Must be an option the question already has")
            @NotNull UUID optionId,
            @NotBlank @Size(max = 1000) String text
    ) {
    }

    CorrectQuestionCommand toCommand(UUID sessionId, UUID questionId) {
        return new CorrectQuestionCommand(sessionId, questionId, correctOptionIds,
                localizations.stream()
                        .map(localization -> new CorrectQuestionCommand.CorrectedLocalization(
                                localization.languageCode(),
                                localization.prompt(),
                                localization.options() == null
                                        ? List.of()
                                        : localization.options().stream()
                                                .map(option -> new CorrectQuestionCommand.CorrectedOption(
                                                        option.optionId(), option.text()))
                                                .toList()))
                        .toList());
    }
}
