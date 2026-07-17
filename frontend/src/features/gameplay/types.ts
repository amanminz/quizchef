import type { SessionPhase as BackendQuestionPhase } from "@/types/api";

/**
 * The client-side gameplay finite state machine — centralized here so
 * every gameplay component renders off one authoritative phase instead of
 * inferring it from scattered conditionals (the PR #4 guiding principle,
 * extended — not redesigned — by PR #5 with the results phases). Derived
 * purely from server-reported state (session state + current question's
 * backend phase, {@link BackendQuestionPhase}); the frontend never decides
 * a transition, only reflects one that already happened.
 *
 * - `LOBBY` — the session has not started (should not normally be reached
 *   from the gameplay route; a defensive fallback).
 * - `COUNTDOWN` — IN_PROGRESS, but the host has not opened the first
 *   question yet. No server countdown duration exists (RFC-004 has no
 *   such setting) — this is an honest "get ready" state, not a client
 *   timer.
 * - `QUESTION_OPEN` — a question is open for answers.
 * - `WAITING` — the question closed but the answer is not yet revealed
 *   (the backend's QUESTION_CLOSED). PR #4 collapsed everything after the
 *   close into this state; PR #5 narrowed it to exactly QUESTION_CLOSED
 *   now that the later phases render real content.
 * - `ANSWER_REVEALED` — correctness (and the author's explanation) are on
 *   the wire; the reveal screen renders.
 * - `LEADERBOARD` — the standings screen renders.
 * - `FINISHED` — the session has ended (FINISHED or ARCHIVED); final
 *   results and the session summary render.
 */
export type GameplayPhase =
  | "LOBBY"
  | "COUNTDOWN"
  | "QUESTION_OPEN"
  | "WAITING"
  | "ANSWER_REVEALED"
  | "LEADERBOARD"
  | "FINISHED";

export type { BackendQuestionPhase };
