import { useEffect, useRef } from "react";
import { LeaderboardRow } from "@/features/gameplay/components/LeaderboardRow";
import type { LeaderboardEntryDto } from "@/types/api";

export interface LeaderboardTableProps {
  /** The server's ranked standings, rendered verbatim and in order. */
  entries: LeaderboardEntryDto[];
  /** The viewer's participant id, to highlight their own row. */
  ownParticipantId?: string;
  caption?: string;
}

/**
 * The standings, as a real table (caption + column headers) so screen
 * readers get rows and rank/score relationships for free. Rankings are
 * the server's, rendered in the order received — never sorted or computed
 * here. Score deltas and rank movement are the one client-side touch: a
 * diff against the *previous server snapshot* this table rendered, kept
 * in a ref — presentation of two server states, absent on the first
 * render after a refresh (there is nothing truthful to diff against).
 */
export function LeaderboardTable({ entries, ownParticipantId, caption }: LeaderboardTableProps) {
  const previousRef = useRef<Map<string, LeaderboardEntryDto>>(new Map());
  const previous = previousRef.current;

  useEffect(() => {
    previousRef.current = new Map(
      entries.map((entry) => [entry.participantId ?? "", entry])
    );
  }, [entries]);

  return (
    <div className="overflow-x-auto rounded-lg border">
      <table className="w-full border-collapse">
        <caption className="sr-only">{caption ?? "Leaderboard"}</caption>
        <thead>
          <tr className="border-b bg-muted/50 text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            <th scope="col" className="px-3 py-2">
              Rank
            </th>
            <th scope="col" className="px-3 py-2">
              Player
            </th>
            <th scope="col" className="px-3 py-2 text-right">
              Score
            </th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => {
            const before = previous.get(entry.participantId ?? "");
            return (
              <LeaderboardRow
                key={entry.participantId}
                entry={entry}
                isOwn={ownParticipantId !== undefined && entry.participantId === ownParticipantId}
                scoreDelta={
                  before?.score !== undefined && entry.score !== undefined
                    ? entry.score - before.score
                    : undefined
                }
                rankMovement={
                  before?.rank !== undefined && entry.rank !== undefined
                    ? before.rank - entry.rank
                    : undefined
                }
              />
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
