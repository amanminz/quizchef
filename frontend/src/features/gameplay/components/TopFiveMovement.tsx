import { ArrowDown, ArrowUp, Minus, Sparkles } from "lucide-react";
import { cn } from "@/utils/cn";

export interface TopFiveMovementProps {
  /** The server's rank on the previous board; absent means "was not on it". */
  previousRank: number | undefined;
  /** The server's rank on the current board. */
  currentRank: number | undefined;
}

/**
 * How far a row travelled, stated from the two ranks the server gave for
 * it — a description of two authoritative boards, never a ranking decided
 * here.
 *
 * Every state carries a word or a number next to its icon ("2", "New Top
 * 5", "Same"), so movement never depends on the colour of an arrow to be
 * understood — the accessibility rule this component exists to keep in
 * one place, and the reason a projector at the back of a hall stays
 * readable too.
 *
 * A missing previous rank is "New Top 5", never a distance: the backend
 * withholds the rank of someone who was outside the projected board
 * precisely so no fabricated "up 4" can be shown for a position the room
 * never saw.
 */
export function TopFiveMovement({ previousRank, currentRank }: TopFiveMovementProps) {
  if (currentRank === undefined) {
    return null;
  }
  if (previousRank === undefined) {
    return (
      <span
        className={cn(indicator, "border-success/40 bg-success/10 text-success")}
        aria-label="New in the top 5"
      >
        <Sparkles aria-hidden className="h-[1em] w-[1em]" />
        New Top 5
      </span>
    );
  }

  const places = previousRank - currentRank;
  if (places > 0) {
    return (
      <span
        className={cn(indicator, "border-success/40 bg-success/10 text-success")}
        aria-label={`Up ${places} ${places === 1 ? "place" : "places"}`}
      >
        <ArrowUp aria-hidden className="h-[1em] w-[1em]" />
        {places}
      </span>
    );
  }
  if (places < 0) {
    return (
      <span
        className={cn(indicator, "border-border bg-muted text-muted-foreground")}
        aria-label={`Down ${-places} ${places === -1 ? "place" : "places"}`}
      >
        <ArrowDown aria-hidden className="h-[1em] w-[1em]" />
        {-places}
      </span>
    );
  }
  return (
    <span className={cn(indicator, "border-border text-muted-foreground")} aria-label="No change">
      <Minus aria-hidden className="h-[1em] w-[1em]" />
      Same
    </span>
  );
}

const indicator =
  "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 font-bold tabular-nums";
