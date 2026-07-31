import { CheckCircle2, Megaphone } from "lucide-react";
import { Button } from "@/components/common/Button";
import { ErrorPanel } from "@/components/common/ErrorPanel";

export interface ReleaseResultsButtonProps {
  released: boolean;
  isReleasing: boolean;
  error?: unknown;
  onRelease: () => void;
}

/**
 * The host's one authoritative action after the winner ceremony: lift the
 * final-results hold so every participant may read their own final rank.
 * Idempotent server-side, so a duplicate click is harmless — once
 * released (reflected from the real session state, never local ceremony
 * progress), this renders a confirmation instead of a clickable button,
 * and replaying the podium animation never brings the button back.
 */
export function ReleaseResultsButton({
  released,
  isReleasing,
  error,
  onRelease
}: ReleaseResultsButtonProps) {
  if (released) {
    return (
      <p className="flex items-center gap-2 text-sm font-medium text-success">
        <CheckCircle2 aria-hidden className="h-4 w-4" />
        Results released to participants
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <Button onClick={onRelease} isLoading={isReleasing} disabled={isReleasing}>
        <Megaphone aria-hidden className="h-4 w-4" />
        Reveal Results to Participants
      </Button>
      {error != null && (
        <ErrorPanel title="Could not release results" error={error} onRetry={onRelease} />
      )}
    </div>
  );
}
