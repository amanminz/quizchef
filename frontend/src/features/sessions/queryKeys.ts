/**
 * Centralized TanStack Query keys for the session-hosting feature — same
 * contract as the quizzes feature's registry: a mutation's invalidation and
 * a realtime event's cache write can never drift from a query's key by a
 * typo. There is no list key because the backend has no session list
 * endpoint — the dashboard is a set of detail queries over the locally
 * tracked hosted-session ids (see hostedSessionsStore).
 */
export const sessionKeys = {
  all: ["sessions"] as const,
  details: () => [...sessionKeys.all, "detail"] as const,
  detail: (sessionId: string) => [...sessionKeys.details(), sessionId] as const
};
