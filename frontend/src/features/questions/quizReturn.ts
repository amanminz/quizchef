/**
 * Return-to-quiz context for authoring launched from the quiz Questions
 * step. Deliberately not a free-form returnTo URL: only a quiz id travels
 * in the query string, and only ids that are plain slugs (no separators,
 * nothing that could escape the route template) are honored — return
 * navigation can only ever land on the internal quiz Questions route.
 */

const SAFE_ID_PATTERN = /^[A-Za-z0-9-]+$/;

export function quizIdFromSearch(search: URLSearchParams): string | undefined {
  const quizId = search.get("quizId");
  return quizId && SAFE_ID_PATTERN.test(quizId) ? quizId : undefined;
}

export function quizQuestionsPath(quizId: string): string {
  return `/quizzes/${quizId}/questions`;
}

export function newQuestionPath(quizId?: string): string {
  return quizId ? `/questions/new?quizId=${quizId}` : "/questions/new";
}

export function editQuestionPath(questionId: string, quizId?: string): string {
  const base = `/questions/${questionId}/edit`;
  return quizId ? `${base}?quizId=${quizId}` : base;
}

/** The question's read-only detail page — always keyed by the real question id. */
export function questionDetailPath(questionId: string): string {
  return `/questions/${questionId}`;
}

/**
 * The notice the quiz Questions step (or the library) shows after a
 * return navigation — carried in router state, never in the URL.
 */
export interface AuthoringNotice {
  tone: "success" | "warning";
  message: string;
}
