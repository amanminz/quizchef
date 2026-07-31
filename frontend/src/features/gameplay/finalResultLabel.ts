/**
 * The universal final-placement label (product rule, not data — computed
 * client-side from the server's own `rank`, never recalculated): 1st–3rd
 * are Winners, 4th–5th are Runners-up, 6th onward is just a final rank.
 * Shared by the host's ceremony and the participant's final result so the
 * two screens never disagree.
 */
export function finalResultLabel(rank: number): "Winner" | "Runner-up" | null {
  if (rank >= 1 && rank <= 3) {
    return "Winner";
  }
  if (rank === 4 || rank === 5) {
    return "Runner-up";
  }
  return null;
}
