package io.quizchef.common.api;

import io.quizchef.common.exception.ConflictException;
import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.common.exception.QuizChefException;
import io.quizchef.common.exception.ResourceNotFoundException;
import io.quizchef.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The single global exception handler for every QuizChef API.
 *
 * <p>Maps exception categories to HTTP status codes and renders the shared
 * {@link ApiError} format. Stack traces are never exposed to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(QuizChefException.class)
    public ResponseEntity<ApiError> handleQuizChefException(QuizChefException exception) {
        HttpStatus status = statusOf(exception);
        if (status.is5xxServerError()) {
            log.error("Unmapped domain exception [{}]", exception.errorCode(), exception);
        } else {
            log.info("Request failed [{}]: {}", exception.errorCode(), exception.getMessage());
        }
        return ResponseEntity.status(status)
                .body(ApiError.of(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(MethodArgumentNotValidException exception) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Request validation failed", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("request.malformed", "Request body could not be read"));
    }

    /**
     * Domain aggregates reject invalid construction with
     * IllegalArgumentException; at the API boundary that is a client error,
     * never a server fault (RFC-003).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidArgument(IllegalArgumentException exception) {
        log.info("Request rejected by domain validation: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of("request.invalid", exception.getMessage()));
    }

    /**
     * An optimistic lock lost the race inside a single transaction window.
     * Same contract as a stale client version: refresh and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("conflict.concurrent-modification",
                        "The resource was modified by someone else. Refresh and try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return ResponseEntity.status(status)
                    .body(ApiError.of("http." + status.value(), status.getReasonPhrase()));
        }
        log.error("Unexpected error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("internal.error", "An unexpected error occurred"));
    }

    private HttpStatus statusOf(QuizChefException exception) {
        return switch (exception) {
            case ResourceNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case UnauthorizedException ignored -> HttpStatus.UNAUTHORIZED;
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
