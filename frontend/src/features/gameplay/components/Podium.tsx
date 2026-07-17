import { Trophy } from "lucide-react";
import type { LeaderboardEntryDto } from "@/types/api";
import { cn } from "@/utils/cn";

const placeStyles = [
  { label: "1st", height: "h-28", tone: "bg-primary/15 border-primary/40 text-primary" },
  { label: "2nd", height: "h-20", tone: "bg-muted border-border text-muted-foreground" },
  { label: "3rd", height: "h-14", tone: "bg-muted/60 border-border text-muted-foreground" }
];

/**
 * The top three, as the server ranked them (ties and order included —
 * entries render verbatim). Traditional podium order: second, first,
 * third.
 */
export function Podium({ entries }: { entries: LeaderboardEntryDto[] }) {
  const topThree = entries.slice(0, 3);
  if (topThree.length === 0) {
    return null;
  }
  // Visual order 2-1-3; fewer than three renders what exists.
  const visualOrder = [topThree[1], topThree[0], topThree[2]].filter(
    (entry): entry is LeaderboardEntryDto => entry !== undefined
  );

  return (
    <ol
      aria-label="Podium"
      className="flex items-end justify-center gap-3"
    >
      {visualOrder.map((entry) => {
        const place = (entry.rank ?? 1) - 1;
        const style = placeStyles[Math.min(place, 2)];
        return (
          <li key={entry.participantId} className="flex w-28 flex-col items-center gap-2">
            <span className="flex flex-col items-center gap-0.5 text-center">
              {place === 0 && <Trophy aria-hidden className="h-5 w-5 text-primary" />}
              <span className="max-w-full truncate text-sm font-semibold">{entry.displayName}</span>
              <span className="font-mono text-xs tabular-nums text-muted-foreground">
                {entry.score}
              </span>
            </span>
            <div
              className={cn(
                "flex w-full items-start justify-center rounded-t-md border border-b-0 pt-2 text-sm font-bold",
                style.height,
                style.tone
              )}
            >
              {style.label}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
