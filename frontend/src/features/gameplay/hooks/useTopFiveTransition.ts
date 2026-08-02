import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { GameplayPhase } from "@/features/gameplay/types";

/**
 * Host-only: the two authoritative boards the projected Top 5 animates
 * between — the standings before this question and after it, with each
 * row's previous and current rank, both scores, and the points this
 * question awarded. Every number and the order itself are the server's;
 * the client animates between two given states and never ranks anything
 * (ADR-006).
 *
 * Disabled outright for the quiz's last question, alongside the phase
 * gate — that question has no interim leaderboard at all, so the request
 * is never made rather than made and refused. The backend refuses it too
 * (`session.top-five.not-available`, 409), which is what makes the rule
 * hold for a stale tab or a retry; this hook just keeps the host client
 * from asking a question it already knows the answer to. A 409 is an
 * expected state, never a fault, so it does not retry.
 *
 * Keyed by question id: a transition belongs to one question, and a fresh
 * key per question is what guarantees the next leaderboard can never
 * animate against the previous one's cached boards.
 */
export function useTopFiveTransition(
  sessionId: string | undefined,
  questionId: string | undefined,
  phase: GameplayPhase,
  isLastQuestion: boolean
) {
  const enabled =
    Boolean(sessionId) &&
    Boolean(questionId) &&
    !isLastQuestion &&
    (phase === "ANSWER_REVEALED" || phase === "LEADERBOARD");

  return useQuery({
    queryKey: gameplayKeys.topFiveTransition(sessionId ?? "", questionId ?? ""),
    queryFn: () => sessionApi.topFiveTransition(sessionId!),
    enabled,
    retry: (failureCount, error) => {
      if (
        isApiClientError(error) &&
        (error.code === "session.top-five.not-available" ||
          error.code === "session.no-current-question")
      ) {
        return false;
      }
      return failureCount < 2;
    }
  });
}
