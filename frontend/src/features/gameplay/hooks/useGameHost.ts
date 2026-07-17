import { useCallback, useState } from "react";
import { sessionApi } from "@/api/sessionApi";
import { useGameplay } from "@/features/gameplay/hooks/useGameplay";
import { useResults } from "@/features/gameplay/hooks/useResults";

/**
 * Host-only gameplay orchestration. The host never answers questions and
 * never decides the game's outcome — `nextStep` issues exactly the one
 * host command (RFC-004) the *current, known* phase calls for, and every
 * step is a real server call whose result is what actually changes the
 * phase (ADR-006). PR #4 chained reveal→leaderboard→advance invisibly
 * because none of those phases rendered; PR #5 renders them, so each is
 * now its own visible step: close → **Reveal Answer** → **Show
 * Leaderboard** → **Next Question** (or **Finish Quiz** on the last one —
 * the same advance command; the *server* decides that advancing past the
 * last question finishes the session, the label only reflects what the
 * already-fetched questionNumber/totalQuestions say will happen).
 *
 * Kept entirely separate from `usePlayerGameplay`: same underlying
 * `useGameplay`, but host and participant orchestration never share a
 * hook — their permissions and responsibilities differ even when the UI
 * looks similar.
 */
export function useGameHost(sessionId: string | undefined) {
  const gameplay = useGameplay(sessionId);
  const resultsQuery = useResults(sessionId, gameplay.phase);
  const [isAdvancing, setIsAdvancing] = useState(false);
  const [nextStepError, setNextStepError] = useState<unknown>(null);

  const { phase, question, refetchSession, refetchQuestion } = gameplay;
  const isLastQuestion =
    question?.questionNumber != null &&
    question.totalQuestions != null &&
    question.questionNumber >= question.totalQuestions;

  const nextStepLabel = (() => {
    switch (phase) {
      case "COUNTDOWN":
        return "Start Question";
      case "WAITING":
        return "Reveal Answer";
      case "ANSWER_REVEALED":
        return "Show Leaderboard";
      case "LEADERBOARD":
        return isLastQuestion ? "Finish Quiz" : "Next Question";
      default:
        return "Next";
    }
  })();

  const canAdvance =
    phase === "COUNTDOWN" ||
    // WAITING also covers "the question read hasn't landed yet" — only a
    // confirmed QUESTION_CLOSED is revealable (the server would 409 anyway;
    // this just keeps the button honest).
    (phase === "WAITING" && question?.phase === "QUESTION_CLOSED") ||
    phase === "ANSWER_REVEALED" ||
    phase === "LEADERBOARD";

  const nextStep = useCallback(async () => {
    if (!sessionId || !canAdvance) {
      return;
    }
    setIsAdvancing(true);
    setNextStepError(null);
    try {
      if (phase === "COUNTDOWN") {
        await sessionApi.startQuestion(sessionId);
      } else if (phase === "WAITING") {
        await sessionApi.revealAnswer(sessionId);
      } else if (phase === "ANSWER_REVEALED") {
        await sessionApi.showLeaderboard(sessionId);
      } else if (phase === "LEADERBOARD") {
        await sessionApi.advanceQuestion(sessionId);
      }
      await Promise.all([refetchSession(), refetchQuestion()]);
    } catch (error) {
      setNextStepError(error);
    } finally {
      setIsAdvancing(false);
    }
  }, [sessionId, canAdvance, phase, refetchSession, refetchQuestion]);

  return {
    ...gameplay,
    results: resultsQuery.data,
    isLoadingResults: resultsQuery.isPending && resultsQuery.fetchStatus !== "idle",
    resultsError: resultsQuery.error,
    refetchResults: resultsQuery.refetch,
    nextStepLabel,
    canAdvance,
    nextStep,
    isAdvancing,
    nextStepError
  };
}
