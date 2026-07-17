import { useCallback, useState } from "react";
import { sessionApi } from "@/api/sessionApi";
import { useGameplay } from "@/features/gameplay/hooks/useGameplay";

/**
 * Host-only gameplay orchestration. The host never answers questions and
 * never decides the game's outcome — `nextStep` only sequences the
 * already-existing host commands (RFC-004) that the *current, known*
 * phase requires before the next question can open, and every step is a
 * real server call whose result is what actually changes the phase
 * (ADR-006). No leaderboard UI renders here (out of scope for this PR),
 * but `showLeaderboard` must still be called — the domain's own state
 * machine requires ANSWER_REVEALED → LEADERBOARD before a question can
 * reopen (`Session.openQuestion` accepts only "no phase yet" or
 * LEADERBOARD) — so the frontend fires it as invisible plumbing rather
 * than skipping a step the backend requires.
 *
 * Kept entirely separate from `usePlayerGameplay`: same underlying
 * `useGameplay`, but host and participant orchestration never share a
 * hook — their permissions and responsibilities differ even where the UI
 * looks similar.
 */
export function useGameHost(sessionId: string | undefined) {
  const gameplay = useGameplay(sessionId);
  const [isAdvancing, setIsAdvancing] = useState(false);
  const [nextStepError, setNextStepError] = useState<unknown>(null);

  const { phase, refetchSession, refetchQuestion } = gameplay;
  const backendPhase = gameplay.question?.phase;
  const canAdvance =
    phase === "COUNTDOWN" ||
    (phase === "WAITING" &&
      (backendPhase === "QUESTION_CLOSED" ||
        backendPhase === "ANSWER_REVEALED" ||
        backendPhase === "LEADERBOARD"));

  const nextStep = useCallback(async () => {
    if (!sessionId || !canAdvance) {
      return;
    }
    setIsAdvancing(true);
    setNextStepError(null);
    try {
      if (phase === "COUNTDOWN") {
        await sessionApi.startQuestion(sessionId);
      } else if (backendPhase === "QUESTION_CLOSED") {
        await sessionApi.revealAnswer(sessionId);
        await sessionApi.showLeaderboard(sessionId);
        await sessionApi.advanceQuestion(sessionId);
      } else if (backendPhase === "ANSWER_REVEALED") {
        await sessionApi.showLeaderboard(sessionId);
        await sessionApi.advanceQuestion(sessionId);
      } else if (backendPhase === "LEADERBOARD") {
        await sessionApi.advanceQuestion(sessionId);
      }
      await Promise.all([refetchSession(), refetchQuestion()]);
    } catch (error) {
      setNextStepError(error);
    } finally {
      setIsAdvancing(false);
    }
  }, [sessionId, canAdvance, phase, backendPhase, refetchSession, refetchQuestion]);

  return {
    ...gameplay,
    nextStepLabel: gameplay.phase === "COUNTDOWN" ? "Start Question" : "Next Question",
    canAdvance,
    nextStep,
    isAdvancing,
    nextStepError
  };
}
