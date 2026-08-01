import { AlertTriangle } from "lucide-react";
import { PresentationMetric } from "@/features/gameplay/components/PresentationMetric";
import { useCountdown } from "@/features/gameplay/hooks/useCountdown";
import { cn } from "@/utils/cn";

export interface ProjectorCountdownProps {
  /** The server's close time; renders nothing while the question isn't open. */
  endsAt: string | null | undefined;
  /**
   * Renders through the shared `PresentationMetric` box instead of the
   * giant spotlight markup — same urgency logic and aria-label, sized to
   * match the compact status row's other metrics (e.g. "Answered").
   */
  compact?: boolean;
}

/**
 * Projector-scale countdown for Presentation Mode — readable from the back
 * of a room or on a shared screen. The compact `TimeRemaining`/`QuestionTimer`
 * pair is unaffected; this is a deliberately separate, larger component
 * rendered only when presentation mode is active.
 *
 * Digit width never shifts the surrounding layout as seconds change: a
 * fixed `3ch`-wide box (enough for the largest configurable timer, 300s)
 * plus tabular numerals means "9" and "18" and "120" all center in the
 * same box. Urgency escalates in two steps, never through color alone —
 * a border and icon change at 10s remaining, plus a restrained pulse in
 * the final 5s (Tailwind's `motion-safe:` variant, which already
 * respects `prefers-reduced-motion` at the CSS level — no extra branch
 * needed). No sound.
 */
export function ProjectorCountdown({ endsAt, compact = false }: ProjectorCountdownProps) {
  const { remainingMillis } = useCountdown(endsAt);
  if (!endsAt) {
    return null;
  }

  const totalSeconds = Math.max(0, Math.ceil(remainingMillis / 1000));
  const expired = totalSeconds <= 0;
  const critical = !expired && totalSeconds <= 5;
  const warning = !expired && !critical && totalSeconds <= 10;

  if (compact) {
    return (
      <PresentationMetric
        role="timer"
        aria-label={`${totalSeconds} seconds remaining`}
        label="Time left"
        value={totalSeconds}
        tone={critical ? "critical" : warning ? "warning" : "default"}
        icon={
          (warning || critical) && <AlertTriangle aria-hidden className="h-3.5 w-3.5 shrink-0" />
        }
      />
    );
  }

  return (
    <div
      role="timer"
      aria-label={`${totalSeconds} seconds remaining`}
      className={cn(
        "flex flex-col items-center gap-1 rounded-2xl border-4 px-8 py-4 sm:px-12 sm:py-6",
        expired && "border-muted text-muted-foreground",
        !expired && !warning && !critical && "border-border text-foreground",
        warning && "border-amber-500 text-amber-600 dark:border-amber-400 dark:text-amber-400",
        critical &&
          "border-destructive text-destructive motion-safe:animate-pulse"
      )}
    >
      <span className="flex items-center gap-2 text-sm font-bold uppercase tracking-[0.2em] sm:text-base">
        {(warning || critical) && <AlertTriangle aria-hidden className="h-4 w-4 sm:h-5 sm:w-5" />}
        Time left
      </span>
      <span
        className="min-w-[3ch] text-center font-mono font-black leading-none tabular-nums"
        style={{ fontSize: "clamp(4rem, 18vw, 13rem)" }}
      >
        {totalSeconds}
      </span>
    </div>
  );
}
