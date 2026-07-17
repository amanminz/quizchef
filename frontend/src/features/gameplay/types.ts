import type { SessionPhase as BackendQuestionPhase } from "@/types/api";

/**
 * The client-side gameplay finite state machine — centralized here so
 * every gameplay component renders off one authoritative phase instead of
 * inferring it from scattered conditionals (the PR's guiding principle).
 * Derived purely from server-reported state (session state + current
 * question's backend phase, {@link BackendQuestionPhase}); the frontend
 * never decides a transition, only reflects one that already happened.
 *
 * - `LOBBY` — the session has not started (should not normally be reached
 *   from the gameplay route; a defensive fallback).
 * - `COUNTDOWN` — IN_PROGRESS, but the host has not opened the first
 *   question yet. No server countdown duration exists (RFC-004 has no
 *   such setting) — this is an honest "get ready" state, not a client
 *   timer.
 * - `QUESTION_OPEN` — a question is open for answers.
 * - `WAITING` — the question closed; collapses the backend's
 *   QUESTION_CLOSED / ANSWER_REVEALED / LEADERBOARD phases into one state
 *   because this PR renders no reveal or leaderboard UI (out of scope) —
 *   the participant only needs to know "the question is no longer open".
 * - `FINISHED` — the session has ended (FINISHED or ARCHIVED).
 */
export type GameplayPhase = "LOBBY" | "COUNTDOWN" | "QUESTION_OPEN" | "WAITING" | "FINISHED";

export type { BackendQuestionPhase };
