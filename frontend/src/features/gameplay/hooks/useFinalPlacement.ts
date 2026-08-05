import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { GameplayPhase } from "@/features/gameplay/types";

/**
 * A participant's own finish — the only participant-facing source of
 * final ranking there is.
 *
 * Enabled only at `FINISHED`, which `useGameplayState` reports exclusively
 * once the session has ended *and* the host has released results: while
 * the ceremony is still running the phase is `FINAL_RESULTS_PENDING` and
 * this never fires. That is belt and braces over the server's own hold —
 * it refuses until release either way — but it keeps the device from
 * asking a question it has been told it is too early for, and it means no
 * placement is ever sitting in the cache during the ceremony.
 */
export function useFinalPlacement(
  sessionId: string | undefined,
  participantId: string | undefined,
  phase: GameplayPhase
) {
  return useQuery({
    queryKey: gameplayKeys.finalPlacement(sessionId ?? "", participantId ?? ""),
    queryFn: () => sessionApi.finalPlacement(sessionId!, participantId!),
    enabled: sessionId !== undefined && participantId !== undefined && phase === "FINISHED",
    retry: (failureCount, error) => {
      if (
        isApiClientError(error) &&
        (error.code === "session.results.not-available" ||
          error.code === "session.participant.not-found" ||
          // A backend that predates this endpoint (a staggered deploy —
          // the two Railway services do not land together). Retrying a
          // route that does not exist only delays the honest answer.
          isEndpointMissing(error))
      ) {
        return false;
      }
      return failureCount < 2;
    }
  });
}

/**
 * The endpoint itself isn't there, as opposed to refusing to answer.
 * Spring's fallback maps an unrouted request to `http.404`, whereas a real
 * unknown participant is `session.participant.not-found` — so the two are
 * distinguishable, and only the former means "this server is older than
 * this client".
 *
 * It matters because the two want opposite screens: a genuinely missing
 * participant is an error worth surfacing, while a missing *route* during
 * a deploy should look like results simply not being out yet.
 */
export function isEndpointMissing(error: unknown): boolean {
  return isApiClientError(error) && error.code === "http.404";
}
