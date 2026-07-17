import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { GameplayPhase } from "@/features/gameplay/types";

const RESULTS_PHASES: readonly GameplayPhase[] = ["ANSWER_REVEALED", "LEADERBOARD", "FINISHED"];

/**
 * The session's standings — interim (reveal/leaderboard) and final
 * (FINISHED) come from the same read, so one hook serves both the
 * between-questions leaderboard and the completion screens (no separate
 * useLeaderboard: that would be a second cache entry for the same backend
 * state). Enabled only in the phases where the server will answer; a
 * `session.results.not-available` 409 (the FSM and the server can be a
 * beat apart at a phase boundary) is a momentary state, not a fault.
 * The rankings themselves are never computed client-side — every row here
 * is the server's own projection (ADR-006).
 */
export function useResults(sessionId: string | undefined, phase: GameplayPhase) {
  return useQuery({
    queryKey: gameplayKeys.results(sessionId ?? ""),
    queryFn: () => sessionApi.results(sessionId!),
    enabled: sessionId !== undefined && RESULTS_PHASES.includes(phase),
    retry: (failureCount, error) => {
      if (isApiClientError(error) && error.code === "session.results.not-available") {
        return false;
      }
      return failureCount < 2;
    }
  });
}
