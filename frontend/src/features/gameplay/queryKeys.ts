/**
 * Query keys for the gameplay feature — the question in play and the
 * standings read. Session summaries themselves stay under the sessions
 * feature's own `sessionKeys` (reused, not duplicated): gameplay only adds
 * the resources sessions doesn't have.
 */
export const gameplayKeys = {
  all: ["gameplay"] as const,
  currentQuestions: () => [...gameplayKeys.all, "current-question"] as const,
  currentQuestion: (sessionId: string) => [...gameplayKeys.currentQuestions(), sessionId] as const,
  allResults: () => [...gameplayKeys.all, "results"] as const,
  results: (sessionId: string) => [...gameplayKeys.allResults(), sessionId] as const,
  /** Host-only: the current question's answered/eligible counts. */
  answerProgress: (sessionId: string) =>
    [...gameplayKeys.all, "answer-progress", sessionId] as const,
  /**
   * The participant's own result — deliberately a separate key from the
   * host's full standings: role-specific contracts, role-specific caches
   * (live-event privacy). A participant device never mounts `results`.
   */
  personalResult: (sessionId: string, participantId: string) =>
    [...gameplayKeys.all, "personal-result", sessionId, participantId] as const,
  /** Host-only: per-option accepted-answer counts once the question is revealed. */
  answerDistribution: (sessionId: string) =>
    [...gameplayKeys.all, "answer-distribution", sessionId] as const,
  /** A participant's own rank plus immediate neighbours — never the full leaderboard. */
  rankContext: (sessionId: string, participantId: string) =>
    [...gameplayKeys.all, "rank-context", sessionId, participantId] as const,
  /**
   * Host-only: the before/after Top 5 the projected leaderboard animates
   * between. Keyed by question as well as session — the transition *is*
   * one question's, so the next question can never render against the
   * previous one's cached boards (the stale-animation failure mode).
   */
  topFiveTransitions: (sessionId: string) =>
    [...gameplayKeys.all, "top-five", sessionId] as const,
  topFiveTransition: (sessionId: string, questionId: string) =>
    [...gameplayKeys.topFiveTransitions(sessionId), questionId] as const
};
