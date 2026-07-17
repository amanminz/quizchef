package io.quizchef.quiz.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of "My Quizzes" — a concrete, documented shape rather than
 * Spring's own {@code Page} JSON, which leaks framework internals into the
 * public contract.
 */
public record QuizPageResponse(
        List<QuizSummaryResponse> items,
        @Schema(description = "Zero-based page index") int page,
        int size,
        long totalElements,
        int totalPages
) {

    static QuizPageResponse from(Page<QuizSummaryResponse> page) {
        return new QuizPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
