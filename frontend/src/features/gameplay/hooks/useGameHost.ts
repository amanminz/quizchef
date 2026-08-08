import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { sessionApi } from "@/api/sessionApi";
import { isConvergentConflict } from "@/features/gameplay/convergence";
import { useAnswerDistribution } from "@/features/gameplay/hooks/useAnswerDistribution";
import { useAnswerProgress } from "@/features/gameplay/hooks/useAnswerProgress";
import { useGameplay } from "@/features/gameplay/hooks/useGameplay";
import { useReleaseFinalResults } from "@/features/gameplay/hooks/useReleaseFinalResults";
import { useFinalStandings } from "@/features/gameplay/hooks/useFinalStandings";
import { useResults } from "@/features/gameplay/hooks/useResults";
import { useTopFiveTransition } from "@/features/gameplay/hooks/useTopFiveTransition";
import { isLastQuestion as computeIsLastQuestion } from "@/features/gameplay/isLastQuestion";

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
  const progressQuery = useAnswerProgress(sessionId, gameplay.phase);
  const distributionQuery = useAnswerDistribution(sessionId, gameplay.phase);
  const releaseMutation = useReleaseFinalResults(sessionId);
  // History, read back rather than recomputed — available from the moment
  // the session ends, independent of the release gate, because it is the
  // host's own administrative record rather than anything participants see.
  const finalStandingsQuery = useFinalStandings(
    sessionId,
    gameplay.session?.state === "FINISHED" || gameplay.session?.state === "ARCHIVED"
  );
  const [isAdvancing, setIsAdvancing] = useState(false);
  const [nextStepError, setNextStepError] = useState<unknown>(null);

  const { phase, question, refetchSession, refetchQuestion } = gameplay;
  const isLastQuestion = computeIsLastQuestion(question);
  const currentQuestionId = question?.questionId;

  // The projected Top 5, and the two boards it animates between. Never
  // requested for the last question — that question has no interim
  // leaderboard at all (the server refuses it outright), which is exactly
  // what keeps a leaderboard from flashing between the final reveal and
  // the podium.
  const topFiveQuery = useTopFiveTransition(sessionId, currentQuestionId, phase, isLastQuestion);
  const topFiveTransition = useMemo(
    // Belt and braces against a stale cache: a transition is one
    // question's, and a board from the previous question must never be
    // handed to the animation, whatever the cache happens to hold.
    () =>
      topFiveQuery.data?.questionId === currentQuestionId ? topFiveQuery.data : undefined,
    [topFiveQuery.data, currentQuestionId]
  );

  const [animationCompletedFor, setAnimationCompletedFor] = useState<string | null>(null);
  const markLeaderboardAnimationComplete = useCallback(
    () => setAnimationCompletedFor(currentQuestionId ?? null),
    [currentQuestionId]
  );
  // The host's next step waits for the board to settle — advancing
  // mid-count would cut the room off before it has seen the new order.
  // Only ever true while there is genuinely an animation to wait for: if
  // the projection is missing or failed, the game is never blocked on it.
  const isLeaderboardAnimating =
    phase === "LEADERBOARD" &&
    topFiveTransition !== undefined &&
    animationCompletedFor !== currentQuestionId;

  // A real failure that the game then moves past is no longer worth
  // showing: the host is looking at a screen that has already changed.
  const lastPhase = useRef(phase);
  useEffect(() => {
    if (lastPhase.current !== phase) {
      lastPhase.current = phase;
      setNextStepError(null);
    }
  }, [phase]);

  const nextStepLabel = (() => {
    switch (phase) {
      case "COUNTDOWN":
        return "Start Question";
      case "QUESTION_OPEN":
        return "Close Question";
      case "WAITING":
        return "Reveal Answer";
      case "ANSWER_REVEALED":
        // The last question has no leaderboard step to show — its
        // standings are the podium's, so the reveal is followed by the
        // ceremony and nothing in between.
        return isLastQuestion ? "Finish Quiz" : "Show Leaderboard";
      case "LEADERBOARD":
        return "Next Question";
      default:
        return "Next";
    }
  })();

  const canAdvance =
    !isLeaderboardAnimating &&
    (phase === "COUNTDOWN" ||
    // While the question is open the host may close it early — the timer
    // would otherwise; the server's FSM guarantees exactly one of the two
    // transitions wins (the loser's 409 is benign, see nextStep).
    phase === "QUESTION_OPEN" ||
    // WAITING also covers "the question read hasn't landed yet" — only a
    // confirmed QUESTION_CLOSED is revealable (the server would 409 anyway;
    // this just keeps the button honest).
      (phase === "WAITING" && question?.phase === "QUESTION_CLOSED") ||
      phase === "ANSWER_REVEALED" ||
      phase === "LEADERBOARD");

  const nextStep = useCallback(async () => {
    if (!sessionId || !canAdvance || isAdvancing) {
      return;
    }
    setIsAdvancing(true);
    setNextStepError(null);
    try {
      if (phase === "COUNTDOWN") {
        await sessionApi.startQuestion(sessionId);
      } else if (phase === "QUESTION_OPEN") {
        await sessionApi.closeQuestion(sessionId);
      } else if (phase === "WAITING") {
        await sessionApi.revealAnswer(sessionId);
      } else if (phase === "ANSWER_REVEALED") {
        // The last question skips the leaderboard step entirely and
        // finishes the session — the server refuses to show a leaderboard
        // there, and accepts this advance straight from the reveal for
        // exactly that reason.
        if (isLastQuestion) {
          await sessionApi.advanceQuestion(sessionId);
        } else {
          await sessionApi.showLeaderboard(sessionId);
        }
      } else if (phase === "LEADERBOARD") {
        await sessionApi.advanceQuestion(sessionId);
      }
      await Promise.all([refetchSession(), refetchQuestion()]);
    } catch (error) {
      if (isConvergentConflict(error)) {
        // The game is ahead of this client, not broken — the timer closed
        // the question, or a second click already did. Re-read the server
        // and render what it says. Never reissue the command: it lost a
        // race, and whatever state replaced it is not what it was aimed at.
        await Promise.all([refetchSession(), refetchQuestion()]);
        setNextStepError(null);
      } else {
        setNextStepError(error);
      }
    } finally {
      setIsAdvancing(false);
    }
  }, [sessionId, canAdvance, isAdvancing, phase, isLastQuestion, refetchSession, refetchQuestion]);

  const answerProgress =
    phase === "QUESTION_OPEN" || phase === "WAITING" ? progressQuery.data : undefined;
  const allAnswered =
    answerProgress !== undefined &&
    (answerProgress.eligibleCount ?? 0) > 0 &&
    answerProgress.answeredCount === answerProgress.eligibleCount;

  return {
    ...gameplay,
    results: resultsQuery.data,
    isLoadingResults: resultsQuery.isPending && resultsQuery.fetchStatus !== "idle",
    resultsError: resultsQuery.error,
    refetchResults: resultsQuery.refetch,
    /** The backend's answered/eligible counts while a question is in play. */
    answerProgress,
    /**
     * Everyone eligible has answered — emphasize (never auto-fire) the
     * host's next transition. Guarded against zero eligible participants.
     */
    allAnswered,
    /** Per-option accepted-answer counts, available once the answer is revealed. */
    answerDistribution: distributionQuery.data,
    answerDistributionError: distributionQuery.error,
    /**
     * The projected Top 5's before-and-after boards. Always undefined for
     * the quiz's last question — there is no interim leaderboard there.
     */
    topFiveTransition,
    isLoadingTopFive: topFiveQuery.isPending && topFiveQuery.fetchStatus !== "idle",
    /** True while the board is still animating — the gate on the next step. */
    isLeaderboardAnimating,
    /** Called by the leaderboard once it has settled. */
    markLeaderboardAnimationComplete,
    isLastQuestion,
    /** The standings captured when this session ended; undefined until it has. */
    finalStandings: finalStandingsQuery.data,
    /** Whether the host has released final standings to participants. */
    finalResultsReleased: gameplay.session?.finalResultsReleased ?? false,
    releaseFinalResults: () => releaseMutation.mutateAsync(),
    isReleasingFinalResults: releaseMutation.isPending,
    releaseFinalResultsError: releaseMutation.error,
    nextStepLabel,
    canAdvance,
    nextStep,
    isAdvancing,
    nextStepError
  };
}
