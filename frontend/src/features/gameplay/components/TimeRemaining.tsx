import { cn } from "@/utils/cn";

/** The remaining time as mm:ss, in a high-contrast state once time is short. */
export function TimeRemaining({ remainingMillis }: { remainingMillis: number }) {
  const totalSeconds = Math.ceil(remainingMillis / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  const urgent = totalSeconds <= 5 && totalSeconds > 0;
  const expired = totalSeconds <= 0;

  return (
    <span
      className={cn(
        "font-mono text-lg font-bold tabular-nums",
        expired && "text-muted-foreground",
        urgent && "text-destructive"
      )}
    >
      {minutes}:{String(seconds).padStart(2, "0")}
    </span>
  );
}
