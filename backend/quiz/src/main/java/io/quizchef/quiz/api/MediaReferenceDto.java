package io.quizchef.quiz.api;

import io.quizchef.quiz.domain.MediaReference;
import io.quizchef.quiz.domain.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A pointer to media in object storage, shared across all translations.
 * Uploads are the media module's concern (RFC-007).
 */
public record MediaReferenceDto(
        @Schema(description = "Server-assigned when omitted") UUID id,
        @NotNull MediaType mediaType,
        @Schema(example = "media/burning-bush.png") @NotBlank @Size(max = 512) String storageKey,
        @Schema(example = "Burning bush") @Size(max = 500) String altText,
        @Schema(example = "1") @NotNull @Min(1) Integer displayOrder
) {

    MediaReference toReference() {
        return new MediaReference(id == null ? UUID.randomUUID() : id,
                mediaType, storageKey, altText, displayOrder);
    }

    static MediaReferenceDto from(MediaReference reference) {
        return new MediaReferenceDto(reference.id(), reference.mediaType(),
                reference.storageKey(), reference.altText(), reference.displayOrder());
    }
}
