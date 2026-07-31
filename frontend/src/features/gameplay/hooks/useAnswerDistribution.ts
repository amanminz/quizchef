import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { GameplayPhase } from "@/features/gameplay/types";

/**
 * Host-only per-option answer counts for the question in play. Enabled
 * only once the answer has been revealed — before that the server refuses
 * with `session.distribution.not-available` (409), a momentary state, not
 * a fault. Rides the existing `answer.revealed` broadcast as its
 * invalidation signal (see `useGameplay`), exactly like `useResults`.
 */
export function useAnswerDistribution(sessionId: string | undefined, phase: GameplayPhase) {
  const enabled = Boolean(sessionId) && (phase === "ANSWER_REVEALED" || phase === "LEADERBOARD");
  return useQuery({
    queryKey: gameplayKeys.answerDistribution(sessionId ?? ""),
    queryFn: () => sessionApi.answerDistribution(sessionId ?? ""),
    enabled,
    retry: (failureCount, error) => {
      if (
        isApiClientError(error) &&
        (error.code === "session.distribution.not-available" ||
          error.code === "session.no-current-question")
      ) {
        return false;
      }
      return failureCount < 2;
    }
  });
}
