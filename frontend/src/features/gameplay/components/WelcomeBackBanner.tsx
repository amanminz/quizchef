import { PartyPopper } from "lucide-react";

export interface WelcomeBackBannerProps {
  displayName: string;
  score: number;
}

/**
 * The reassurance a returning player actually wants: their name, and the
 * points still on their account.
 *
 * <p>ADR-003 asked for a "Welcome back" from the beginning, and it matters
 * more than it sounds. Someone who has just spent two questions staring at
 * a dead phone does not trust that their score survived — and if the app
 * says nothing, the natural next move is to rejoin "properly", which is
 * exactly how a player ends up as a second participant on zero.
 */
export function WelcomeBackBanner({ displayName, score }: WelcomeBackBannerProps) {
  return (
    <div
      role="status"
      className="mb-4 flex items-center gap-3 rounded-lg border border-success/40 bg-success/10 px-4 py-3"
    >
      <PartyPopper aria-hidden className="h-5 w-5 shrink-0 text-success" />
      <div>
        <p className="font-medium">Welcome back, {displayName}!</p>
        <p className="text-sm text-muted-foreground">
          Your score: {score.toLocaleString()} points
        </p>
      </div>
    </div>
  );
}
