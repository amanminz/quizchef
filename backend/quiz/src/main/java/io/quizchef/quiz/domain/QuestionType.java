package io.quizchef.quiz.domain;

/**
 * How a question is answered. Each type carries its own structural rules,
 * enforced by the Question aggregate:
 *
 * <ul>
 *   <li>SINGLE_CHOICE — exactly one correct option</li>
 *   <li>MULTIPLE_CHOICE — one or more correct options</li>
 *   <li>TRUE_FALSE — exactly two options, exactly one correct</li>
 * </ul>
 */
public enum QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE
}
