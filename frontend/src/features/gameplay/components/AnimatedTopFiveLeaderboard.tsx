import { useEffect, useMemo } from "react";
import { Button } from "@/components/common/Button";
import { TopFiveMovement } from "@/features/gameplay/components/TopFiveMovement";
import {
  ROW_MOVE_DURATION_MS,
  useTopFiveAnimation,
  type TopFiveAnimationStage
} from "@/features/gameplay/hooks/useTopFiveAnimation";
import type { TopFiveLeaderboardEntry, TopFiveLeaderboardTransitionResponse } from "@/types/api";
import { cn } from "@/utils/cn";

export interface AnimatedTopFiveLeaderboardProps {
  /** The server's before-and-after boards. Rendered verbatim, never sorted here. */
  transition: TopFiveLeaderboardTransitionResponse;
  /** Projector layout: larger type, taller rows, tighter chrome. */
  presentationActive?: boolean;
  /**
   * Fired once the board has settled — the host's "Next Question" waits
   * on this so the room is never advanced out from under the animation.
   */
  onComplete?: () => void;
}

/**
 * The between-questions leaderboard: the top five, and only the top five,
 * animated from the standings before this question to the standings after
 * it.
 *
 * Everything numeric here is the server's. The component receives two
 * finished boards and renders them; it does not rank, re-sort, total, or
 * decide who tied — two equal scores at two different ranks are drawn at
 * those two ranks, because the ranking service already broke that tie and
 * is the only thing entitled to.
 *
 * <b>The movement is a transform, not a re-render.</b> Every row is a
 * stable element keyed by participant id, absolutely positioned at
 * `rank - 1` slots down the board; changing rank changes the row's
 * `translateY`, so the browser animates the travel and a row never
 * disappears and reappears somewhere else. That is also what makes the
 * ranks readable while they move — the element carrying a name is the
 * same element throughout.
 *
 * Row heights are fixed (a CSS variable, so the projector layout can be
 * taller without any other change) because both the slot arithmetic and
 * the "no scrolling on a projector" requirement depend on knowing exactly
 * how tall the board is: five rows, never more, never a scroll container.
 */
export function AnimatedTopFiveLeaderboard({
  transition,
  presentationActive = false,
  onComplete
}: AnimatedTopFiveLeaderboardProps) {
  const { stage, scores, isComplete, skip } = useTopFiveAnimation(transition);

  useEffect(() => {
    if (isComplete) {
      onComplete?.();
    }
  }, [isComplete, onComplete]);

  const current = useMemo(() => withIds(transition.currentTopFive), [transition]);
  const previous = useMemo(() => withIds(transition.previousTopFive), [transition]);

  // DOM order is the authoritative *current* order, so a screen reader
  // reads the real standings regardless of where the animation is. Rows
  // on their way out follow, marked decorative: they exist for the count-up
  // step and then leave, and announcing a departed player as part of the
  // top five would simply be wrong.
  const rows = useMemo(() => {
    const currentIds = new Set(current.map((entry) => entry.participantId));
    const leaving = previous.filter((entry) => !currentIds.has(entry.participantId));
    return [...current.map((entry) => ({ entry, leaving: false })),
      ...leaving.map((entry) => ({ entry, leaving: true }))];
  }, [current, previous]);

  const slots = Math.max(current.length, previous.length);
  const rowHeight = presentationActive ? "clamp(3.25rem, 9vh, 5.5rem)" : "3.25rem";

  return (
    <section
      className={cn("flex flex-col", presentationActive ? "h-full min-h-0 gap-3" : "gap-4")}
      aria-label={`Top 5 after question ${transition.questionNumber ?? ""}`}
    >
      <header className="flex shrink-0 flex-wrap items-center justify-between gap-3">
        <h2
          className="font-bold tracking-tight"
          style={{
            fontSize: presentationActive ? "clamp(1.25rem, 3vw, 2.25rem)" : "1.25rem"
          }}
        >
          Leaderboard — Question {transition.questionNumber ?? ""}
        </h2>
        {!isComplete && (
          <Button variant="outline" size="sm" onClick={skip}>
            Skip Animation
          </Button>
        )}
      </header>

      <ol
        className="relative w-full list-none"
        style={
          {
            "--top-five-row-height": rowHeight,
            height: `calc(var(--top-five-row-height) * ${Math.max(slots, 1)})`
          } as React.CSSProperties
        }
      >
        {rows.map(({ entry, leaving }) => (
          <TopFiveRow
            key={entry.participantId}
            entry={entry}
            score={scores[entry.participantId] ?? entry.currentScore ?? 0}
            stage={stage}
            leaving={leaving}
            presentationActive={presentationActive}
          />
        ))}
      </ol>
    </section>
  );
}

function TopFiveRow({
  entry,
  score,
  stage,
  leaving,
  presentationActive
}: {
  entry: IdentifiedEntry;
  score: number;
  stage: TopFiveAnimationStage;
  leaving: boolean;
  presentationActive: boolean;
}) {
  const entering = entry.previousRank === undefined;
  // Slot: where this row sits *now*. During the count-up nobody has moved
  // yet, so everyone holds their previous position — that is the whole
  // point of the first step, and it is why the slot is the previous rank
  // rather than the current one until the board reorders.
  const slot = (stage === "scores" ? entry.previousRank ?? entry.currentRank : entry.currentRank ??
    entry.previousRank) ?? 1;
  // A row that is not on the board yet (an entrant, before the reorder) or
  // no longer on it (a leaver, after) is present but not shown, so its
  // arrival and departure are a fade rather than a mount.
  const visible = stage === "scores" ? !entering : !leaving;
  const displayedRank = stage === "scores" ? entry.previousRank : entry.currentRank;

  return (
    <li
      aria-hidden={leaving || undefined}
      className={cn(
        // A four-column grid rather than flex: the rank, score, and movement
        // columns get reserved width, so a long name shortens itself instead
        // of pushing the numbers off the side of the projector. `minmax(0,
        // 1fr)` on the name is what actually permits that — a grid child
        // defaults to min-content and would otherwise refuse to shrink.
        "absolute inset-x-0 top-0 grid items-center gap-3 rounded-xl border bg-card px-3",
        "motion-safe:transition-all motion-safe:ease-out sm:gap-4 sm:px-4",
        entering && "border-success/50"
      )}
      style={{
        gridTemplateColumns: presentationActive
          ? "minmax(3rem, auto) minmax(0, 1fr) minmax(7rem, auto) minmax(4.5rem, auto)"
          : "minmax(2rem, auto) minmax(0, 1fr) minmax(4rem, auto) minmax(3.5rem, auto)",
        height: "calc(var(--top-five-row-height) - 0.5rem)",
        transform: `translateY(calc(var(--top-five-row-height) * ${slot - 1}))`,
        opacity: visible ? 1 : 0,
        transitionDuration: `${ROW_MOVE_DURATION_MS}ms`
      }}
    >
      <span
        className="text-center font-black tabular-nums text-muted-foreground"
        style={{ fontSize: presentationActive ? "clamp(1.75rem, 3vw, 3.5rem)" : "1rem" }}
      >
        {displayedRank ?? ""}
      </span>
      {/* min-w-0 is load-bearing: without it the truncation never engages
          and a long name wins the column fight against the score. The full
          name stays available to assistive tech and on hover. */}
      <span
        className="min-w-0 truncate font-semibold"
        style={{ fontSize: presentationActive ? "clamp(1.6rem, 2.6vw, 3.1rem)" : "1rem" }}
        title={entry.displayName}
      >
        {entry.displayName}
      </span>
      <span className="flex items-center justify-end gap-2">
        {(entry.pointsEarned ?? 0) > 0 && (
          <span
            className="shrink-0 rounded-full bg-success/15 px-2 py-0.5 font-bold tabular-nums text-success"
            style={{
              fontSize: presentationActive ? "clamp(1rem, 1.5vw, 1.6rem)" : "0.75rem"
            }}
          >
            +{entry.pointsEarned}
          </span>
        )}
        <span
          className="text-right font-mono font-black tabular-nums"
          style={{
            fontSize: presentationActive ? "clamp(1.6rem, 2.6vw, 3.1rem)" : "1rem",
            // Tabular numerals plus a reserved width: the score column holds
            // still while the count-up runs, instead of nudging the row.
            minWidth: "5ch"
          }}
        >
          {score.toLocaleString()}
        </span>
      </span>
      <span
        className="flex justify-end"
        style={{ fontSize: presentationActive ? "clamp(1.2rem, 1.8vw, 2rem)" : "0.75rem" }}
      >
        {stage !== "scores" && (
          <TopFiveMovement previousRank={entry.previousRank} currentRank={entry.currentRank} />
        )}
      </span>
    </li>
  );
}

/** An entry we can key a row by — the id is what keeps a row one row. */
type IdentifiedEntry = TopFiveLeaderboardEntry & { participantId: string };

function withIds(entries: TopFiveLeaderboardEntry[] | undefined): IdentifiedEntry[] {
  return (entries ?? []).filter(
    (entry): entry is IdentifiedEntry => typeof entry.participantId === "string"
  );
}
