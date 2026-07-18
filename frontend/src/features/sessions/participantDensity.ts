export type WallDensity = "large" | "medium" | "compact" | "dense";

/** Count-based density: fewer players, bigger cards — no per-name math. */
export function participantDensity(playerCount: number): WallDensity {
  if (playerCount <= 10) {
    return "large";
  }
  if (playerCount <= 25) {
    return "medium";
  }
  if (playerCount <= 50) {
    return "compact";
  }
  return "dense";
}
