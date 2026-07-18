import { useEffect, useRef, useState } from "react";
import { WaitingState } from "@/features/sessions/components/WaitingState";
import { participantDensity, type WallDensity } from "@/features/sessions/participantDensity";
import type { SessionParticipantDto } from "@/types/api";
import { cn } from "@/utils/cn";

const densityStyles: Record<WallDensity, { grid: string; card: string }> = {
  large: {
    grid: "grid-cols-[repeat(auto-fill,minmax(14rem,1fr))] gap-3",
    card: "px-5 py-4 text-2xl"
  },
  medium: {
    grid: "grid-cols-[repeat(auto-fill,minmax(11rem,1fr))] gap-2.5",
    card: "px-4 py-3 text-xl"
  },
  compact: {
    grid: "grid-cols-[repeat(auto-fill,minmax(9rem,1fr))] gap-2",
    card: "px-3 py-2 text-base"
  },
  dense: {
    grid: "grid-cols-[repeat(auto-fill,minmax(7.5rem,1fr))] gap-1.5",
    card: "px-2 py-1.5 text-sm"
  }
};

export interface ParticipantWallProps {
  participants: SessionParticipantDto[];
  /** The server's authoritative roster size (session summary). */
  totalCount: number;
  /** The latest roster change, announced to assistive tech. */
  announcement: string;
  /** Larger headline and full-height wall for the projected layout. */
  presentation?: boolean;
}

/**
 * The projection-friendly lobby wall: a count legible from the back of
 * the room, and every joined name in stable join order (the server's
 * order — realtime updates never reshuffle a projected display). Density
 * adapts to the player count; names truncate safely with the full value
 * preserved for assistive tech and hover. Newly joined names highlight
 * briefly (motion-safe).
 */
export function ParticipantWall({
  participants,
  totalCount,
  announcement,
  presentation = false
}: ParticipantWallProps) {
  const density = participantDensity(participants.length);
  const styles = densityStyles[density];

  // Names present in the previous render — a newcomer highlights briefly.
  const seenIdsRef = useRef<Set<string>>(new Set());
  const [recentIds, setRecentIds] = useState<Set<string>>(new Set());
  useEffect(() => {
    const seen = seenIdsRef.current;
    const isFirstRender = seen.size === 0 && participants.length > 1;
    const newcomers = participants
      .map((participant) => participant.participantId ?? "")
      .filter((id) => id !== "" && !seen.has(id));
    newcomers.forEach((id) => seen.add(id));
    // A refresh repopulates the whole wall — that is not 40 "new" players.
    if (isFirstRender || newcomers.length === 0) {
      return;
    }
    setRecentIds((previous) => new Set([...previous, ...newcomers]));
    const timer = setTimeout(() => {
      setRecentIds((previous) => {
        const next = new Set(previous);
        newcomers.forEach((id) => next.delete(id));
        return next;
      });
    }, 3_000);
    return () => clearTimeout(timer);
  }, [participants]);

  return (
    <section aria-label="Players joined" className="flex min-w-0 flex-col">
      <div
        className={cn("sticky top-0 z-10 bg-background pb-3 text-center", presentation && "pt-2")}
      >
        <p className="text-sm font-semibold uppercase tracking-widest text-muted-foreground">
          Players joined
        </p>
        <p
          className={cn(
            "font-bold tabular-nums leading-none",
            presentation ? "text-8xl" : "text-6xl"
          )}
          data-testid="player-count"
        >
          {totalCount}
        </p>
      </div>
      <div aria-live="polite" role="status" className="sr-only">
        {announcement}
      </div>

      {totalCount === 0 ? (
        <WaitingState />
      ) : (
        <ul
          className={cn(
            "grid content-start overflow-y-auto",
            styles.grid,
            presentation ? "max-h-[70vh]" : "max-h-[55vh]"
          )}
        >
          {participants.map((participant) => (
            <li
              key={participant.participantId}
              title={participant.displayName}
              className={cn(
                "min-w-0 rounded-md border bg-card text-center font-semibold transition-colors motion-safe:duration-500",
                styles.card,
                participant.connected === false && "opacity-50",
                recentIds.has(participant.participantId ?? "") &&
                  "border-primary bg-primary/10 text-primary"
              )}
            >
              <span className="block truncate">{participant.displayName}</span>
            </li>
          ))}
          {totalCount > participants.length && (
            <li className="min-w-0 rounded-md border border-dashed px-3 py-2 text-center text-sm text-muted-foreground">
              +{totalCount - participants.length} more
            </li>
          )}
        </ul>
      )}
    </section>
  );
}
