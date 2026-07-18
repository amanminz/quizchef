import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import { RESULTS_PHASES } from "@/features/gameplay/hooks/useResults";
import type { GameplayPhase } from "@/features/gameplay/types";

/**
 * The participant's own result — rank, score, framing counts, nothing
 * about anyone else. The participant-side counterpart of the host's
 * `useResults`: same phase gating, same 409-is-momentary handling, but a
 * role-specific contract and cache key. A participant device never calls
 * the full-standings endpoint (live-event privacy — the server would
 * refuse it anyway).
 */
export function useParticipantResult(
  sessionId: string | undefined,
  participantId: string | undefined,
  phase: GameplayPhase
) {
  return useQuery({
    queryKey: gameplayKeys.personalResult(sessionId ?? "", participantId ?? ""),
    queryFn: () => sessionApi.participantResult(sessionId!, participantId!),
    enabled:
      sessionId !== undefined && participantId !== undefined && RESULTS_PHASES.includes(phase),
    retry: (failureCount, error) => {
      if (isApiClientError(error) && error.code === "session.results.not-available") {
        return false;
      }
      return failureCount < 2;
    }
  });
}
