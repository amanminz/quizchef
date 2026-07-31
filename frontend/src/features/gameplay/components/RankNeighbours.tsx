import { ArrowDown, ArrowUp, Users } from "lucide-react";
import { rankOrdinal } from "@/features/gameplay/rankOrdinal";
import type { ParticipantRankContextResponse } from "@/types/api";

export interface RankNeighboursProps {
  context: ParticipantRankContextResponse;
}

/**
 * The immediate ranking neighbours after a non-final question — the
 * participant's nearest rival ahead and behind, or who they're tied with.
 * Deliberately narrow: this device never has the full leaderboard to draw
 * from (the server sends only these rows — live-event privacy), so there
 * is nothing here to accidentally over-render. Renders nothing when the
 * server reports no neighbours at all (a session of one).
 */
export function RankNeighbours({ context }: RankNeighboursProps) {
  const { ahead, behind, tiedWith } = context;
  if (!ahead && !behind && !tiedWith) {
    return null;
  }
  // Genuinely alone at the top: no one ahead, no tie, someone behind.
  const leading = !ahead && !tiedWith && behind !== undefined;

  return (
    <section
      aria-label="Ranking neighbours"
      className="flex flex-col gap-3 rounded-lg border bg-card px-5 py-4 text-sm"
    >
      {leading && <p className="font-semibold text-foreground">You are leading</p>}
      {tiedWith && (
        <p className="flex items-center gap-1.5 font-semibold text-foreground">
          <Users aria-hidden className="h-4 w-4 text-muted-foreground" />
          Tied with {tiedWith.displayName}
        </p>
      )}
      {ahead && (
        <div className="flex flex-col gap-0.5">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Ahead of you
          </p>
          <p className="flex items-center gap-1.5 text-foreground">
            <ArrowUp aria-hidden className="h-4 w-4 text-success" />
            {rankOrdinal(ahead.rank ?? 0)} · {ahead.displayName} · {ahead.scoreDifference} points
            ahead
          </p>
        </div>
      )}
      {behind && (
        <div className="flex flex-col gap-0.5">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Behind you
          </p>
          <p className="flex items-center gap-1.5 text-foreground">
            <ArrowDown aria-hidden className="h-4 w-4 text-muted-foreground" />
            {rankOrdinal(behind.rank ?? 0)} · {behind.displayName} · {behind.scoreDifference} points
            behind
          </p>
        </div>
      )}
    </section>
  );
}
