import type { FinalStandingsResponse } from "@/types/api";

export interface FinalStandingsTableProps {
  standings: FinalStandingsResponse;
}

/**
 * A finished session's standings, as they were captured when it ended.
 *
 * Rendered in the order received and never re-sorted: these ranks are the
 * ones that game produced, read back from history rather than recomputed,
 * so re-deriving them here would defeat the point of storing them.
 *
 * Host-facing administrative history — the full field, unlike anything a
 * participant sees. A real table so screen readers get the rank/name/score
 * relationships, with the name column allowed to shrink so a long one
 * truncates instead of pushing the score out of view.
 */
export function FinalStandingsTable({ standings }: FinalStandingsTableProps) {
  const entries = standings.entries ?? [];

  if (entries.length === 0) {
    // A session that finished before this history existed, or one that
    // ended with nobody in it. Saying so is better than an empty table
    // that reads as a loading state that never resolved.
    return (
      <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
        No standings were recorded for this session.
      </p>
    );
  }

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
        Final standings
      </h2>
      <div className="rounded-lg border">
        <table className="w-full table-fixed border-collapse">
          <caption className="sr-only">
            Final standings, captured when the session ended
          </caption>
          <thead>
            <tr className="border-b bg-muted/50 text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              <th scope="col" className="w-16 px-3 py-2">
                Rank
              </th>
              <th scope="col" className="px-3 py-2">
                Participant
              </th>
              <th scope="col" className="w-28 px-3 py-2 text-right">
                Points
              </th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.participantId} className="border-b last:border-b-0">
                <td className="px-3 py-2.5 text-sm tabular-nums text-muted-foreground">
                  {entry.rank}
                </td>
                {/* The name as it read at completion — not a live lookup. */}
                <td className="max-w-0 truncate px-3 py-2.5 text-sm" title={entry.displayName}>
                  {entry.displayName}
                </td>
                <td className="px-3 py-2.5 text-right font-mono text-sm font-semibold tabular-nums">
                  {(entry.score ?? 0).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
