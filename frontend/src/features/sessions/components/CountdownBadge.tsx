import { Timer } from "lucide-react";

/**
 * The per-question time limit as a compact badge. This is quiz metadata
 * (RFC-003 settings), not a live countdown — live countdowns render
 * against the server's `endsAt` and belong to the gameplay PR (ADR-006).
 */
export function CountdownBadge({ seconds }: { seconds: number | undefined }) {
  if (!seconds) {
    return null;
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
      <Timer aria-hidden className="h-3.5 w-3.5" />
      {seconds}s per question
    </span>
  );
}
