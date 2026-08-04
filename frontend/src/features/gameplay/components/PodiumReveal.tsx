import { Crown, RotateCcw, SkipForward } from "lucide-react";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Button } from "@/components/common/Button";
import { finalResultLabel } from "@/features/gameplay/finalResultLabel";
import { Podium } from "@/features/gameplay/components/Podium";
import { RevealedStandingsPages } from "@/features/gameplay/components/RevealedStandingsPages";
import { usePrefersReducedMotion } from "@/features/gameplay/hooks/usePrefersReducedMotion";
import { rankOrdinal } from "@/features/gameplay/rankOrdinal";
import type { LeaderboardEntryDto } from "@/types/api";
import { cn } from "@/utils/cn";

/** How many places get their own moment in the staged reveal (5th → 1st). */
const CEREMONY_PLACES = 5;

/** One reveal step: pause `holdMillis`, then show `revealCount` places. */
interface RevealStage {
  revealCount: number;
  holdMillis: number;
}

const CONFETTI_PIECES = 24;

export interface PodiumRevealProps {
  /** Scopes the played-once memory — a refresh shows the finished podium. */
  sessionId: string;
  /** The server's final standings, verbatim (ties and order included). */
  entries: LeaderboardEntryDto[];
  /**
   * How many places may be shown publicly — the server's
   * `exactRankRevealCount`. The ceremony reveals the podium and then the
   * rest of that group, and stops: the remainder of `entries` is the
   * host's administrative data and never reaches the projector, because
   * the room is watching and the people in it did not agree to have their
   * exact position announced.
   */
  exactRankRevealCount: number;
  /** Rendered once the reveal completes (summary cards, actions). */
  footer?: ReactNode;
}

/**
 * The host's staged winner reveal: fifth, fourth, third, second, then
 * first — the crown last — then the podium (top 3) and, in fixed
 * projector-sized pages, the rest of the reveal group. Purely local display
 * state: Skip and Replay never emit gameplay commands or touch session
 * state, and the reveal plays once per session (a refresh re-fetches
 * authoritative results and renders the completed podium, it never
 * re-runs the ceremony uninvited). With reduced motion, the same content
 * appears without movement or confetti. Small fields render honestly:
 * fewer than five finishers reveal only what exists — never an empty
 * placeholder. The caller's `footer` (which should include the host's
 * release-results action) renders only once the ceremony has completed
 * or been skipped — never during the countdown.
 */
export function PodiumReveal({
  sessionId,
  entries,
  exactRankRevealCount,
  footer
}: PodiumRevealProps) {
  const reducedMotion = usePrefersReducedMotion();
  const storageKey = `quizchef.podium-played.${sessionId}`;
  const topFive = entries.slice(0, CEREMONY_PLACES);

  // suspense (0 revealed) → fifth, fourth, third, second, first → all revealed.
  const stages = useMemo<RevealStage[]>(() => {
    const reveal: RevealStage[] = [{ revealCount: 0, holdMillis: 1_300 }];
    for (let count = 1; count < topFive.length; count++) {
      reveal.push({ revealCount: count, holdMillis: 1_500 });
    }
    reveal.push({ revealCount: topFive.length, holdMillis: 2_200 });
    return reveal;
  }, [topFive.length]);

  const alreadyPlayed = () => sessionStorage.getItem(storageKey) !== null;
  const [stageIndex, setStageIndex] = useState(() =>
    reducedMotion || alreadyPlayed() || topFive.length === 0 ? stages.length : 0
  );
  const complete = stageIndex >= stages.length;

  useEffect(() => {
    if (complete) {
      sessionStorage.setItem(storageKey, "played");
      return;
    }
    const timer = setTimeout(
      () => setStageIndex((index) => index + 1),
      stages[stageIndex].holdMillis
    );
    return () => clearTimeout(timer);
  }, [complete, stageIndex, stages, storageKey]);

  const revealCount = complete ? topFive.length : stages[stageIndex].revealCount;
  // Everything the room may see, minus the three already on the podium.
  // Cut from the server's own count, never recomputed here.
  const revealedBelowPodium = entries.slice(3, Math.max(3, exactRankRevealCount));
  // Worst place first: fifth appears before fourth, before third, and so on.
  const revealed = topFive.slice(topFive.length - revealCount).reverse();

  if (complete) {
    return (
      <div className="flex flex-col gap-6">
        <div className="relative">
          {!reducedMotion && <Confetti />}
          <Podium entries={entries} />
        </div>
        {revealedBelowPodium.length > 0 && (
          <RevealedStandingsPages entries={revealedBelowPodium} />
        )}
        <div className="flex justify-center">
          <Button variant="ghost" size="sm" onClick={() => setStageIndex(0)}>
            <RotateCcw aria-hidden className="h-4 w-4" />
            Replay podium
          </Button>
        </div>
        {footer}
      </div>
    );
  }

  return (
    <div className="relative flex min-h-[24rem] flex-col items-center justify-center gap-4 overflow-hidden rounded-lg border bg-card px-6 py-10">
      {revealCount === 0 ? (
        <p className="text-2xl font-semibold text-muted-foreground motion-safe:animate-pulse">
          And the winners are…
        </p>
      ) : (
        <ol aria-label="Winner reveal" className="flex w-full max-w-md flex-col-reverse gap-3">
          {revealed.map((entry) => {
            const isFirst = (entry.rank ?? 0) === 1;
            const label = finalResultLabel(entry.rank ?? 0);
            return (
              <li
                key={entry.participantId}
                className={cn(
                  "flex items-center gap-4 rounded-lg border px-5 py-4 motion-safe:animate-[podium-rise_0.6s_ease-out]",
                  isFirst ? "border-primary/60 bg-primary/10" : "bg-background"
                )}
              >
                <span
                  className={cn(
                    "text-2xl font-bold tabular-nums",
                    isFirst ? "text-primary" : "text-muted-foreground"
                  )}
                >
                  {rankOrdinal(entry.rank ?? 0)}
                </span>
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate text-xl font-semibold">{entry.displayName}</span>
                  {label && (
                    <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      {label}
                    </span>
                  )}
                </span>
                {isFirst && <Crown aria-hidden className="h-6 w-6 text-primary" />}
                <span className="font-mono text-lg tabular-nums text-muted-foreground">
                  {entry.score}
                </span>
              </li>
            );
          })}
        </ol>
      )}
      <Button
        variant="ghost"
        size="sm"
        className="absolute bottom-3 right-3"
        onClick={() => setStageIndex(stages.length)}
      >
        <SkipForward aria-hidden className="h-4 w-4" />
        Skip animation
      </Button>
    </div>
  );
}

/** Tasteful falling pieces behind the podium — motion-safe render only. */
function Confetti() {
  const pieces = useMemo(
    () =>
      Array.from({ length: CONFETTI_PIECES }, (_, index) => ({
        left: `${(index * 41) % 100}%`,
        delay: `${((index * 137) % 900) / 1000}s`,
        hue: [`bg-primary`, `bg-success`, `bg-secondary`][index % 3]
      })),
    []
  );
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
      {pieces.map((piece, index) => (
        <span
          key={index}
          className={cn(
            "absolute top-0 h-2 w-1.5 rounded-sm motion-safe:animate-[confetti-fall_2.6s_ease-in_forwards]",
            piece.hue
          )}
          style={{ left: piece.left, animationDelay: piece.delay }}
        />
      ))}
    </div>
  );
}
