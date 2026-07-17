import { RankMovement } from "@/features/gameplay/components/RankMovement";
import { ScoreDelta } from "@/features/gameplay/components/ScoreDelta";
import type { LeaderboardEntryDto } from "@/types/api";
import { cn } from "@/utils/cn";

export interface LeaderboardRowProps {
  entry: LeaderboardEntryDto;
  /** Highlights the viewer's own row (participant screens). */
  isOwn?: boolean;
  scoreDelta?: number;
  rankMovement?: number;
}

/** One ranked row — real table semantics; the numbers are the server's verbatim. */
export function LeaderboardRow({ entry, isOwn, scoreDelta, rankMovement }: LeaderboardRowProps) {
  return (
    <tr className={cn("border-b last:border-b-0", isOwn && "bg-primary/5 font-semibold")}>
      <td className="w-12 px-3 py-2.5 text-sm tabular-nums text-muted-foreground">
        <span className="flex items-center gap-1.5">
          {entry.rank}
          <RankMovement movement={rankMovement} />
        </span>
      </td>
      <td className="px-3 py-2.5 text-sm">
        <span className="flex items-center gap-2">
          <span className="truncate">{entry.displayName}</span>
          {isOwn && (
            <span className="shrink-0 rounded-full bg-primary/15 px-1.5 py-0.5 text-[10px] font-bold uppercase text-primary">
              You
            </span>
          )}
        </span>
      </td>
      <td className="px-3 py-2.5 text-right text-sm">
        <span className="inline-flex items-center gap-2">
          <ScoreDelta delta={scoreDelta} />
          <span className="font-mono font-semibold tabular-nums">{entry.score}</span>
        </span>
      </td>
    </tr>
  );
}
