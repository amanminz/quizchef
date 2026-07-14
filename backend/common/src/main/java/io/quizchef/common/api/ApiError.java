package io.quizchef.common.api;

import java.time.Instant;
import java.util.List;

/**
 * The single error response format for every QuizChef API.
 *
 * @param code        stable machine-readable error code
 * @param message     human-readable summary, safe to display
 * @param timestamp   when the error occurred (UTC)
 * @param fieldErrors per-field validation failures, empty unless the request
 *                    failed bean validation
 */
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        List<ApiFieldError> fieldErrors
) {

    public ApiError {
        fieldErrors = List.copyOf(fieldErrors);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), List.of());
    }

    public static ApiError validation(String message, List<ApiFieldError> fieldErrors) {
        return new ApiError("validation.failed", message, Instant.now(), fieldErrors);
    }
}
