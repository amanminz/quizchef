package io.quizchef.common.api;

import io.quizchef.common.correlation.CorrelationKeys;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

/**
 * The single error response format for every QuizChef API.
 *
 * @param code          stable machine-readable error code
 * @param message       human-readable summary, safe to display
 * @param timestamp     when the error occurred (UTC)
 * @param correlationId the request's correlation id, so a support report or a
 *                      fatal-error dialog can be matched back to server logs
 *                      (Phase 3 PR #2); {@code null} only when an error is
 *                      built outside a request thread
 * @param fieldErrors   per-field validation failures, empty unless the
 *                      request failed bean validation
 */
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String correlationId,
        List<ApiFieldError> fieldErrors
) {

    public ApiError {
        fieldErrors = List.copyOf(fieldErrors);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), currentCorrelationId(), List.of());
    }

    public static ApiError validation(String message, List<ApiFieldError> fieldErrors) {
        return new ApiError("validation.failed", message, Instant.now(), currentCorrelationId(), fieldErrors);
    }

    private static String currentCorrelationId() {
        return MDC.get(CorrelationKeys.CORRELATION_ID_MDC_KEY);
    }
}
