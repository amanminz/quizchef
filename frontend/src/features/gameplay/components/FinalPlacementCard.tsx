import { rankOrdinal } from "@/features/gameplay/rankOrdinal";
import type { ParticipantFinalPlacementResponse } from "@/types/api";

export interface FinalPlacementCardProps {
  placement: ParticipantFinalPlacementResponse;
  /** The closing line from the catalogue, in the player's language. */
  message: string;
}

const LABEL_TEXT: Record<string, string> = {
  WINNER: "Winner",
  RUNNER_UP: "Runner-up",
  FINALIST: "Finalist"
};

/**
 * How this participant finished, in whichever of the two shapes the
 * server decided for them.
 *
 * The branch is on `visibility` and nothing else. This component never
 * works out which group someone is in, never compares their rank to a
 * cutoff, and could not show an exact rank to the relative-only group if
 * it wanted to — that response has no rank on it. The server draws the
 * line; this renders whichever side it was handed.
 */
export function FinalPlacementCard({ placement, message }: FinalPlacementCardProps) {
  const score = (placement.score ?? 0).toLocaleString();

  if (placement.visibility === "EXACT_RANK") {
    const rank = placement.rank ?? 0;
    const label = placement.label ? LABEL_TEXT[placement.label] : null;
    return (
      <section
        aria-label="Your final result"
        className="flex flex-col items-center gap-2 rounded-lg border bg-card px-6 py-8 text-center"
      >
        {label && (
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-primary">{label}</p>
        )}
        <p className="text-sm font-semibold uppercase tracking-widest text-muted-foreground">
          You finished
        </p>
        <p className="text-6xl font-bold leading-none">{rankOrdinal(rank)}</p>
        <p className="text-sm text-muted-foreground">
          Final score: <span className="font-mono font-semibold text-foreground">{score}</span>
        </p>
        <p className="max-w-sm text-pretty pt-1 text-sm font-medium text-foreground/80">{message}</p>
      </section>
    );
  }

  // Relative-only. The headline is the quiz finishing, not a placement —
  // there is no number to lead with, and inventing a gentler-sounding one
  // ("mid-table", "the pack") would be the same disclosure in nicer words.
  return (
    <section
      aria-label="Your final result"
      className="flex flex-col items-center gap-2 rounded-lg border bg-card px-6 py-8 text-center"
    >
      <p className="text-2xl font-bold tracking-tight">Quiz complete!</p>
      <p className="text-sm text-muted-foreground">
        Final score: <span className="font-mono font-semibold text-foreground">{score}</span>
      </p>
      <NeighbourContext placement={placement} />
      <p className="max-w-sm text-pretty pt-1 text-sm font-medium text-foreground/80">{message}</p>
    </section>
  );
}

/**
 * Who they finished near, in the wording the server's own shape supports.
 * `tiedWith` gets "alongside" rather than "ahead of": claiming to have
 * beaten someone the ranking calls an equal is exactly the small
 * inaccuracy this whole feature exists to avoid.
 */
function NeighbourContext({ placement }: { placement: ParticipantFinalPlacementResponse }) {
  const lines: string[] = [];
  if (placement.tiedWith?.displayName) {
    lines.push(`You finished alongside ${placement.tiedWith.displayName}`);
  }
  if (placement.aheadOf?.displayName) {
    lines.push(`You finished ahead of ${placement.aheadOf.displayName}`);
  }
  if (placement.behind?.displayName) {
    lines.push(`just behind ${placement.behind.displayName}`);
  }
  if (lines.length === 0) {
    return null;
  }
  // "You finished ahead of David and just behind Amelia." — one sentence
  // when both neighbours exist, and gracefully shorter when one doesn't.
  return (
    <p className="max-w-sm text-pretty text-base text-foreground">
      {lines.join(lines.length > 1 ? " and " : "")}.
    </p>
  );
}
