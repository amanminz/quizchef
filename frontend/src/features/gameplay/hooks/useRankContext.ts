import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { GameplayPhase } from "@/features/gameplay/types";

/**
 * A participant's own rank plus the immediate neighbours ahead/behind —
 * never the full leaderboard. Enabled only for a non-final question whose
 * answer has been revealed; the quiz's last question always refuses
 * (`session.rank-context.not-available`, 409) since final standings are
 * held for the host's winner ceremony instead — a momentary/expected
 * state, not a fault, so it never retries.
 */
export function useRankContext(
  sessionId: string | undefined,
  participantId: string | undefined,
  phase: GameplayPhase,
  isLastQuestion: boolean
) {
  const enabled =
    Boolean(sessionId) &&
    Boolean(participantId) &&
    !isLastQuestion &&
    (phase === "ANSWER_REVEALED" || phase === "LEADERBOARD");
  return useQuery({
    queryKey: gameplayKeys.rankContext(sessionId ?? "", participantId ?? ""),
    queryFn: () => sessionApi.rankContext(sessionId ?? "", participantId ?? ""),
    enabled,
    retry: (failureCount, error) => {
      if (isApiClientError(error) && error.code === "session.rank-context.not-available") {
        return false;
      }
      return failureCount < 2;
    }
  });
}
