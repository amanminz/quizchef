import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { SessionSummaryResponse } from "@/types/api";

/**
 * The question in play — enabled only once the session summary itself
 * says one exists (IN_PROGRESS with a currentQuestionId), so this never
 * fetches during the lobby or after the session finishes. A
 * `session.no-current-question` 409 is a legitimate, momentary state (the
 * summary and this query can be a beat apart right at a phase boundary),
 * not a fault — `useGameplayState` treats it as "no question yet" rather
 * than surfacing it as an error.
 */
export function useCurrentQuestion(
  sessionId: string | undefined,
  session: SessionSummaryResponse | undefined
) {
  const hasCurrentQuestion = session?.state === "IN_PROGRESS" && session.currentQuestionId != null;

  return useQuery({
    queryKey: gameplayKeys.currentQuestion(sessionId ?? ""),
    queryFn: () => sessionApi.currentQuestion(sessionId!),
    enabled: sessionId !== undefined && hasCurrentQuestion,
    retry: (failureCount, error) => {
      if (isApiClientError(error) && error.code === "session.no-current-question") {
        return false;
      }
      return failureCount < 2;
    }
  });
}
