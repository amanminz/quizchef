/**
 * Query keys for the gameplay feature — the question in play. Session
 * summaries themselves stay under the sessions feature's own `sessionKeys`
 * (reused, not duplicated): gameplay only adds the one resource sessions
 * doesn't have.
 */
export const gameplayKeys = {
  all: ["gameplay"] as const,
  currentQuestions: () => [...gameplayKeys.all, "current-question"] as const,
  currentQuestion: (sessionId: string) => [...gameplayKeys.currentQuestions(), sessionId] as const
};
