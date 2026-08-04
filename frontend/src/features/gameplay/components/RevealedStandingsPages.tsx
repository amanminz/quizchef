import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/common/Button";
import type { LeaderboardEntryDto } from "@/types/api";

/** Rows per page — chosen to fit a projector without scrolling. */
const ROWS_PER_PAGE = 10;

export interface RevealedStandingsPagesProps {
  /**
   * The places to show, already cut to the backend's reveal group by the
   * caller. Rendered verbatim, in order.
   */
  entries: LeaderboardEntryDto[];
}

/**
 * The standings below the podium, in fixed projector-sized pages.
 *
 * Pages rather than a scroll container, because this renders on a screen
 * nobody can scroll — the host is at a lectern and the audience is thirty
 * feet away. A page always holds the same number of rows, so the layout
 * never reflows between pages and the host can read ahead.
 *
 * This component does not know where the reveal group ends; it renders
 * exactly the rows it is given. The cut is the server's
 * (`exactRankRevealCount`), applied by the caller, so there is no second
 * opinion about it here to drift from the participants' own screens.
 */
export function RevealedStandingsPages({ entries }: RevealedStandingsPagesProps) {
  const [page, setPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(entries.length / ROWS_PER_PAGE));
  const current = Math.min(page, pageCount - 1);
  const rows = entries.slice(current * ROWS_PER_PAGE, (current + 1) * ROWS_PER_PAGE);

  if (entries.length === 0) {
    return null;
  }

  const first = rows[0]?.rank ?? 0;
  const last = rows[rows.length - 1]?.rank ?? 0;

  return (
    <section aria-label="Revealed standings" className="flex flex-col gap-3">
      <header className="flex items-center justify-between gap-3">
        <h3 className="text-lg font-bold tracking-tight">
          Ranks {first}–{last}
        </h3>
        {pageCount > 1 && (
          <div className="flex items-center gap-2">
            <span className="text-sm tabular-nums text-muted-foreground">
              Page {current + 1} of {pageCount}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={current === 0}
              onClick={() => setPage(current - 1)}
            >
              <ChevronLeft aria-hidden className="h-4 w-4" />
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={current >= pageCount - 1}
              onClick={() => setPage(current + 1)}
            >
              Next
              <ChevronRight aria-hidden className="h-4 w-4" />
            </Button>
          </div>
        )}
      </header>

      <ol className="flex flex-col gap-2">
        {rows.map((entry) => (
          <li
            key={entry.participantId}
            className="flex items-center gap-4 rounded-lg border bg-card px-4 py-2.5"
          >
            <span
              className="min-w-[2ch] text-center font-black tabular-nums text-muted-foreground"
              style={{ fontSize: "clamp(1rem, 2vw, 1.6rem)" }}
            >
              {entry.rank}
            </span>
            <span
              className="min-w-0 flex-1 truncate font-semibold"
              style={{ fontSize: "clamp(1rem, 2vw, 1.6rem)" }}
            >
              {entry.displayName}
            </span>
            <span
              className="shrink-0 font-mono font-bold tabular-nums"
              style={{ fontSize: "clamp(1rem, 2vw, 1.6rem)" }}
            >
              {(entry.score ?? 0).toLocaleString()}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
