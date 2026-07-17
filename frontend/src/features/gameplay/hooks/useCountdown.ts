import { useCallback, useEffect, useState } from "react";

/**
 * A display-only countdown to a server timestamp — ticks locally so the UI
 * doesn't need a realtime "tick" event for every second, but the target
 * time always comes from the server's `endsAt` (ADR-006). This never
 * decides that a question has closed; it only renders the clock. The
 * authoritative close is the `question.closed` event, reflected through
 * `useGameplayState`'s phase, not through `isExpired` here.
 */
export function useCountdown(endsAtIso: string | null | undefined): {
  remainingMillis: number;
  isExpired: boolean;
} {
  const computeRemaining = useCallback(() => {
    if (!endsAtIso) {
      return 0;
    }
    return Math.max(0, new Date(endsAtIso).getTime() - Date.now());
  }, [endsAtIso]);

  const [remainingMillis, setRemainingMillis] = useState(computeRemaining);

  useEffect(() => {
    setRemainingMillis(computeRemaining());
    if (!endsAtIso) {
      return;
    }
    const interval = setInterval(() => setRemainingMillis(computeRemaining()), 250);
    return () => clearInterval(interval);
  }, [endsAtIso, computeRemaining]);

  return { remainingMillis, isExpired: endsAtIso != null && remainingMillis <= 0 };
}
