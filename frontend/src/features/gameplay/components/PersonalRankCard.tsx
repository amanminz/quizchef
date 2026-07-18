import { ArrowDown, ArrowUp } from "lucide-react";
import { rankOrdinal } from "@/features/gameplay/rankOrdinal";
import type { ParticipantResultResponse } from "@/types/api";

export interface PersonalRankCardProps {
  result: ParticipantResultResponse;
  /** Points gained since the previous personal snapshot, when known. */
  scoreDelta?: number;
  /** Positive = moved up since the previous personal snapshot. */
  rankDelta?: number;
  /** "final" switches the copy from interim standings to the finish line. */
  variant?: "interim" | "final";
}

/**
 * The participant's own result — and nothing else. No other player's
 * name, score, or rank ever renders on a participant device (live-event
 * privacy); the shared leaderboard belongs to the host's projected
 * screen. Everything shown is the server's verdict (ADR-006); deltas are
 * display diffs of two consecutive personal snapshots.
 */
export function PersonalRankCard({
  result,
  scoreDelta,
  rankDelta,
  variant = "interim"
}: PersonalRankCardProps) {
  const rank = result.rank ?? 0;
  const isFinal = variant === "final";

  return (
    <section
      aria-label={isFinal ? "Your final result" : "Your rank"}
      className="flex flex-col items-center gap-2 rounded-lg border bg-card px-6 py-8 text-center"
    >
      <p className="text-sm font-semibold uppercase tracking-widest text-muted-foreground">
        {isFinal ? "You finished" : "Your rank"}
      </p>
      <p className="text-6xl font-bold leading-none">{rankOrdinal(rank)}</p>
      {rankDelta !== undefined && rankDelta !== 0 && !isFinal && (
        <p
          className={
            rankDelta > 0
              ? "flex items-center gap-1 text-sm font-medium text-success"
              : "flex items-center gap-1 text-sm font-medium text-muted-foreground"
          }
        >
          {rankDelta > 0 ? (
            <ArrowUp aria-hidden className="h-4 w-4" />
          ) : (
            <ArrowDown aria-hidden className="h-4 w-4" />
          )}
          {rankDelta > 0
            ? `Up ${rankDelta} place${rankDelta === 1 ? "" : "s"}`
            : `Down ${-rankDelta} place${rankDelta === -1 ? "" : "s"}`}
        </p>
      )}
      {scoreDelta !== undefined && scoreDelta > 0 && !isFinal && (
        <p className="font-mono text-lg font-semibold text-success">+{scoreDelta} points</p>
      )}
      <p className="text-sm text-muted-foreground">
        {isFinal ? "Final score" : "Total score"}:{" "}
        <span className="font-mono font-semibold text-foreground">
          {(result.score ?? 0).toLocaleString()}
        </span>
      </p>
      <p className="text-xs text-muted-foreground">
        {result.participantCount ?? 0} player{(result.participantCount ?? 0) === 1 ? "" : "s"}
        {isFinal && result.totalQuestions !== undefined
          ? ` · ${result.totalQuestions} questions`
          : ""}
      </p>
    </section>
  );
}
