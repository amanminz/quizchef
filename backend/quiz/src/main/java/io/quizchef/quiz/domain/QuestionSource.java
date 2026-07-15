package io.quizchef.quiz.domain;

/**
 * How a question came into existence. Metadata only — no behavior varies
 * by source yet. AI and IMPORT are reserved for the generation and
 * import/export features (PRD roadmap); the authoring API always creates
 * MANUAL questions.
 */
public enum QuestionSource {
    MANUAL,
    AI,
    IMPORT
}
