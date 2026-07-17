import { Crown } from "lucide-react";
import type { LeaderboardEntryDto } from "@/types/api";

/** The server-determined winner — rank 1 of the final standings, verbatim. */
export function WinnerCard({ winner }: { winner: LeaderboardEntryDto | undefined }) {
  if (!winner) {
    return null;
  }
  return (
    <div className="flex flex-col items-center gap-1 rounded-lg border border-primary/40 bg-primary/5 px-6 py-5 text-center">
      <Crown aria-hidden className="h-7 w-7 text-primary" />
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Winner</p>
      <p className="text-xl font-bold">{winner.displayName}</p>
      <p className="font-mono text-sm tabular-nums text-muted-foreground">
        {winner.score} points
      </p>
    </div>
  );
}
