import { useCallback, useEffect, useMemo, useState } from "react";
import { usePrefersReducedMotion } from "@/features/gameplay/hooks/usePrefersReducedMotion";
import type { TopFiveLeaderboardTransitionResponse } from "@/types/api";

/**
 * The two visible steps of the between-questions leaderboard, in order.
 * They are deliberately sequential rather than concurrent: a row that
 * moves while its own number is still climbing is unreadable at projector
 * distance, and the whole point of the sequence is that the room can see
 * *why* the order changed before it changes.
 *
 * - `scores` — everyone holds their previous position while their score
 *   counts up from what it was to what the server says it is now.
 * - `positions` — the rows travel to their new authoritative ranks.
 * - `done` — settled; the host's next action is enabled.
 */
export type TopFiveAnimationStage = "scores" | "positions" | "done";

/** ~1s of count-up: long enough to read, short enough not to stall the game. */
const SCORE_DURATION_MS = 1000;
const SCORE_FRAME_MS = 50;
const SCORE_STEPS = SCORE_DURATION_MS / SCORE_FRAME_MS;
/** Matches the row transition in `AnimatedTopFiveLeaderboard`'s stylesheet. */
export const ROW_MOVE_DURATION_MS = 700;

export interface TopFiveAnimation {
  stage: TopFiveAnimationStage;
  /** The score to render for each participant right now, by participant id. */
  scores: Record<string, number>;
  /** True once the board is settled — the gate on the host's next step. */
  isComplete: boolean;
  /** Ends the animation immediately at the authoritative final state. */
  skip: () => void;
}

/**
 * Drives the leaderboard's two animation steps. Purely local display
 * state over two server snapshots: it never fetches, never mutates, and
 * never decides a rank — a refresh mid-animation simply starts a new
 * animation (or, with reduced motion, none at all) against whatever the
 * server says now.
 *
 * Counts with `setInterval` rather than `requestAnimationFrame`
 * deliberately: the visual difference at this duration is nil, and timers
 * are what let the tests step through the sequence with fake timers
 * instead of waiting out a real second per case.
 *
 * The final numbers are never computed by the animation — every non-`scores`
 * stage renders `currentScore` verbatim, so the board always lands on
 * exactly the server's values no matter where the easing happened to be.
 */
export function useTopFiveAnimation(
  transition: TopFiveLeaderboardTransitionResponse | undefined
): TopFiveAnimation {
  const reducedMotion = usePrefersReducedMotion();
  const questionId = transition?.questionId;

  // Reduced motion skips straight to the settled board — the same content
  // and the same movement labels, without the movement.
  const initialStage: TopFiveAnimationStage = reducedMotion ? "done" : "scores";
  const [stage, setStage] = useState<TopFiveAnimationStage>(initialStage);
  const [step, setStep] = useState(reducedMotion ? SCORE_STEPS : 0);

  // A new question is a new animation: reset rather than resume, so the
  // next leaderboard can never continue the previous one's count-up.
  useEffect(() => {
    setStage(initialStage);
    setStep(reducedMotion ? SCORE_STEPS : 0);
  }, [questionId, initialStage, reducedMotion]);

  useEffect(() => {
    if (stage !== "scores" || transition === undefined) {
      return;
    }
    const interval = setInterval(() => {
      setStep((current) => {
        const next = current + 1;
        if (next >= SCORE_STEPS) {
          setStage("positions");
          return SCORE_STEPS;
        }
        return next;
      });
    }, SCORE_FRAME_MS);
    return () => clearInterval(interval);
  }, [stage, transition]);

  useEffect(() => {
    if (stage !== "positions") {
      return;
    }
    const timeout = setTimeout(() => setStage("done"), ROW_MOVE_DURATION_MS);
    return () => clearTimeout(timeout);
  }, [stage]);

  const skip = useCallback(() => {
    setStep(SCORE_STEPS);
    setStage("done");
  }, []);

  const scores = useMemo(() => {
    const progress = stage === "scores" ? ease(step / SCORE_STEPS) : 1;
    const byParticipant: Record<string, number> = {};
    for (const entry of [
      ...(transition?.previousTopFive ?? []),
      ...(transition?.currentTopFive ?? [])
    ]) {
      if (entry.participantId === undefined) {
        continue;
      }
      const from = entry.previousScore ?? 0;
      const to = entry.currentScore ?? 0;
      // Exact at the end, always: `progress === 1` short-circuits the
      // arithmetic so no rounding can land the board a point off.
      byParticipant[entry.participantId] =
        progress === 1 ? to : Math.round(from + (to - from) * progress);
    }
    return byParticipant;
  }, [transition, stage, step]);

  return { stage, scores, isComplete: stage === "done", skip };
}

/** Ease-out cubic — fast off the mark, settling gently onto the real total. */
function ease(t: number): number {
  return 1 - Math.pow(1 - t, 3);
}
