package io.quizchef.quiz.domain;

/**
 * Lifecycle of a reusable question (Draft → Published → Archived).
 *
 * <p>Drafts are fully editable. Published questions are immutable — quizzes
 * may rely on them — but stay attachable. Archived questions are unavailable
 * for new quizzes while existing published quizzes keep functioning.
 */
public enum QuestionState {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
