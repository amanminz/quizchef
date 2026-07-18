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
  /**
   * The participant's own result — deliberately a separate key from the
   * host's full standings: role-specific contracts, role-specific caches
   * (live-event privacy). A participant device never mounts `results`.
   */
  personalResult: (sessionId: string, participantId: string) =>
    [...gameplayKeys.all, "personal-result", sessionId, participantId] as const
};
