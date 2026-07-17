import { isApiClientError } from "@/api/apiError";
import { useCurrentQuestion } from "@/features/gameplay/hooks/useCurrentQuestion";
import type { GameplayPhase } from "@/features/gameplay/types";
import { useSession } from "@/features/sessions/hooks/useSession";

/**
 * The gameplay finite state machine (the PR's central recommendation):
 * every gameplay component reads `phase` from here rather than inferring
 * it independently from session state, question phase, and loading flags
 * scattered across components.
 */
export function useGameplayState(sessionId: string | undefined) {
  const sessionQuery = useSession(sessionId);
  const session = sessionQuery.data;
  const questionQuery = useCurrentQuestion(sessionId, session);
  const question = questionQuery.data;

  // A no-current-question 409 is "nothing to show yet", not a fault (see
  // useCurrentQuestion) — every other failure is a real error to surface.
  const questionIsBenignlyAbsent =
    isApiClientError(questionQuery.error) && questionQuery.error.code === "session.no-current-question";

  const phase: GameplayPhase = (() => {
    if (!session) {
      return "LOBBY";
    }
    if (session.state === "FINISHED" || session.state === "ARCHIVED") {
      return "FINISHED";
    }
    if (session.state !== "IN_PROGRESS") {
      return "LOBBY";
    }
    if (!session.currentQuestionId) {
      return "COUNTDOWN";
    }
    if (question?.phase === "QUESTION_OPEN") {
      return "QUESTION_OPEN";
    }
    return "WAITING";
  })();

  return {
    session,
    question,
    phase,
    isLoadingSession: sessionQuery.isPending,
    sessionError: sessionQuery.error,
    isLoadingQuestion: questionQuery.isPending && !questionIsBenignlyAbsent,
    questionError: questionIsBenignlyAbsent ? null : questionQuery.error,
    refetchSession: sessionQuery.refetch,
    refetchQuestion: questionQuery.refetch
  };
}
