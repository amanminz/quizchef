/**
 * The points gained since the previous standings we rendered — a diff of
 * two server snapshots (display only; scores themselves are always the
 * server's). Hidden when nothing changed or no previous snapshot exists
 * (first render after a refresh).
 */
export function ScoreDelta({ delta }: { delta: number | undefined }) {
  if (!delta || delta <= 0) {
    return null;
  }
  return (
    <span className="inline-flex items-center rounded-full bg-success/15 px-2 py-0.5 text-xs font-bold tabular-nums text-success">
      +{delta}
    </span>
  );
}
