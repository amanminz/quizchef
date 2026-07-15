package io.quizchef.quiz.domain;

/**
 * Lifecycle of an authored quiz (PRD: Draft → Published → Archived).
 */
public enum QuizState {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
