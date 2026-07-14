package io.quizchef.common.api;

import io.quizchef.common.exception.ConflictException;
import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.common.exception.QuizChefException;
import io.quizchef.common.exception.ResourceNotFoundException;
import io.quizchef.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
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
