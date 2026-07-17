import { Hourglass } from "lucide-react";

/**
 * Shown between questions (the backend's QUESTION_CLOSED / ANSWER_REVEALED
 * / LEADERBOARD phases, collapsed into one client phase — see
 * `GameplayPhase`). No reveal or leaderboard content renders here by
 * design (out of scope for this PR); the host is the one moving things
 * forward, and nothing here polls for it.
 */
export function WaitingOverlay() {
  return (
    <div
      role="status"
      className="flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-16 text-center"
    >
      <Hourglass aria-hidden className="h-8 w-8 animate-pulse text-muted-foreground" />
      <p className="text-sm font-medium text-muted-foreground">Waiting for next question…</p>
    </div>
  );
}
