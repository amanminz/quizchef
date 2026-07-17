import { Timer } from "lucide-react";
import { useCountdown } from "@/features/gameplay/hooks/useCountdown";
import { TimeRemaining } from "@/features/gameplay/components/TimeRemaining";

/**
 * The ticking question timer — the smart half of the pair with
 * `TimeRemaining` (the presentational readout). Always renders against the
 * server's `endsAt` (ADR-006): if this component receives no `endsAt`
 * (the question isn't open), it renders nothing rather than a fabricated
 * countdown.
 */
export function QuestionTimer({ endsAt }: { endsAt: string | null | undefined }) {
  const { remainingMillis } = useCountdown(endsAt);
  if (!endsAt) {
    return null;
  }
  return (
    <span className="inline-flex items-center gap-1.5">
      <Timer aria-hidden className="h-4 w-4 text-muted-foreground" />
      <TimeRemaining remainingMillis={remainingMillis} />
    </span>
  );
}
