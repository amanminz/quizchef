import { useQuery } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { sessionKeys } from "@/features/sessions/queryKeys";

/**
 * The host's read of the session's own question sequence — numbers,
 * statuses, and the content behind the correction dialog.
 *
 * Host-only in the strong sense: the response carries answer keys for
 * questions the room has not reached. A participant screen must never mount
 * this hook, which is why it lives in the sessions feature (host hosting)
 * rather than gameplay (shared by both roles).
 *
 * Gated on `enabled` so it is not fetched at all until the host opens the
 * panel: there is no reason to hold unrevealed answer keys in a cache the
 * host has not asked to look at.
 */
export function useSessionQuestions(sessionId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: sessionKeys.questions(sessionId ?? ""),
    queryFn: () => sessionApi.sessionQuestions(sessionId!),
    enabled: Boolean(sessionId) && enabled,
    staleTime: 0
  });
}
