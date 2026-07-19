import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { GameplayPhase } from "@/features/gameplay/types";

/**
 * The host's live "5 / 10 answered" read — authoritative counts from the
 * backend, refreshed when `useGameplay` invalidates this key on each
 * answer.progress broadcast (and on roster events, which move the
 * eligible denominator). Host only: participant devices never mount
 * this query. Mounted while a question is open or freshly closed; the
 * benign `session.no-current-question` 409 between questions is treated
 * as "no counts to show", never an error.
 */
export function useAnswerProgress(sessionId: string | undefined, phase: GameplayPhase) {
  const enabled = Boolean(sessionId) && (phase === "QUESTION_OPEN" || phase === "WAITING");
  return useQuery({
    queryKey: gameplayKeys.answerProgress(sessionId ?? ""),
    queryFn: () => sessionApi.answerProgress(sessionId ?? ""),
    enabled,
    retry: (failureCount, error) =>
      !(isApiClientError(error) && error.code === "session.no-current-question") &&
      failureCount < 2
  });
}
