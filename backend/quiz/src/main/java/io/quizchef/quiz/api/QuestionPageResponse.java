package io.quizchef.quiz.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of question library results — a concrete, documented shape
 * rather than Spring's own {@code Page} JSON.
 */
public record QuestionPageResponse(
        List<QuestionSummaryResponse> items,
        @Schema(description = "Zero-based page index") int page,
        int size,
        long totalElements,
        int totalPages
) {

    static QuestionPageResponse from(Page<QuestionSummaryResponse> page) {
        return new QuestionPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
