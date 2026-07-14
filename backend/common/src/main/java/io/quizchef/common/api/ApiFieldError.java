package io.quizchef.common.api;

/**
 * A single field-level validation failure.
 */
public record ApiFieldError(String field, String message) {
}
