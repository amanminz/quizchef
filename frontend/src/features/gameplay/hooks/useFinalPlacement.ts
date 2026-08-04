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
      if (isApiClientError(error) && error.code === "session.results.not-available") {
        return false;
      }
      return failureCount < 2;
    }
  });
}
