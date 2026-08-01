import { useQuery } from "@tanstack/react-query";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import type { GameplayPhase } from "@/features/gameplay/types";

/**
 * The phases in which the participant's *own* result is readable —
 * deliberately excludes `FINAL_RESULTS_PENDING` (unlike the host's
 * `RESULTS_PHASES`): the backend holds a participant's final rank until
 * the host explicitly releases it, so fetching here while pending would
 * only earn a guaranteed 409. Once released, `useGameplayState` reports
 * `FINISHED` instead and this query enables itself.
 *
 * `ANSWER_REVEALED`/`LEADERBOARD` are additionally excluded on the quiz's
 * *last* question — the backend holds the final rank there too, one host
 * click before the session technically finishes (see `isLastQuestion`
 * below and `SessionResultsQueryService.personalResultReadable`).
 */
const PERSONAL_RESULT_PHASES: readonly GameplayPhase[] = [
  "ANSWER_REVEALED",
  "LEADERBOARD",
  "FINISHED"
];

/** The phases held back specifically on the quiz's last question. */
const HELD_ON_LAST_QUESTION: readonly GameplayPhase[] = ["ANSWER_REVEALED", "LEADERBOARD"];

/**
 * The participant's own result — rank, score, framing counts, nothing
 * about anyone else. The participant-side counterpart of the host's
 * `useResults`: same 409-is-momentary handling, but a role-specific
 * contract, cache key, and phase list (see `PERSONAL_RESULT_PHASES`). A
 * participant device never calls the full-standings endpoint (live-event
 * privacy — the server would refuse it anyway).
 */
export function useParticipantResult(
  sessionId: string | undefined,
  participantId: string | undefined,
  phase: GameplayPhase,
  isLastQuestion: boolean
) {
  return useQuery({
    queryKey: gameplayKeys.personalResult(sessionId ?? "", participantId ?? ""),
    queryFn: () => sessionApi.participantResult(sessionId!, participantId!),
    enabled:
      sessionId !== undefined &&
      participantId !== undefined &&
      PERSONAL_RESULT_PHASES.includes(phase) &&
      !(isLastQuestion && HELD_ON_LAST_QUESTION.includes(phase)),
    retry: (failureCount, error) => {
      if (isApiClientError(error) && error.code === "session.results.not-available") {
        return false;
      }
      return failureCount < 2;
    }
  });
}
