import { ArrowDown, ArrowUp, Minus } from "lucide-react";

/**
 * Movement since the previous standings we rendered — again a diff of two
 * server snapshots, never a ranking computed here. Renders nothing without
 * a previous snapshot to compare against.
 */
export function RankMovement({ movement }: { movement: number | undefined }) {
  if (movement === undefined) {
    return null;
  }
  if (movement > 0) {
    return (
      <span className="inline-flex items-center text-success" aria-label={`Up ${movement}`}>
        <ArrowUp aria-hidden className="h-3.5 w-3.5" />
      </span>
    );
  }
  if (movement < 0) {
    return (
      <span
        className="inline-flex items-center text-destructive"
        aria-label={`Down ${Math.abs(movement)}`}
      >
        <ArrowDown aria-hidden className="h-3.5 w-3.5" />
      </span>
    );
  }
  return (
    <span className="inline-flex items-center text-muted-foreground" aria-label="No change">
      <Minus aria-hidden className="h-3.5 w-3.5" />
    </span>
  );
}
