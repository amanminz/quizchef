import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FinalStandingsTable } from "@/features/gameplay/components/FinalStandingsTable";
import type { FinalStandingsResponse } from "@/types/api";

function standings(overrides: Partial<FinalStandingsResponse> = {}): FinalStandingsResponse {
  return {
    sessionId: "session-1",
    capturedAt: "2026-08-07T18:00:00Z",
    entries: [
      { participantId: "p1", displayName: "Amelia", rank: 1, score: 8450 },
      { participantId: "p2", displayName: "Aman", rank: 2, score: 8120 },
      { participantId: "p3", displayName: "Ruth", rank: 3, score: 7780 },
      { participantId: "p4", displayName: "David", rank: 4, score: 6930 },
      { participantId: "p5", displayName: "John", rank: 5, score: 6550 }
    ],
    ...overrides
  };
}

describe("FinalStandingsTable", () => {
  it("renders every participant in the order the session finished", () => {
    render(<FinalStandingsTable standings={standings()} />);

    const rows = screen.getAllByRole("row").slice(1); // drop the header
    expect(rows).toHaveLength(5);
    expect(within(rows[0]).getByText("Amelia")).toBeInTheDocument();
    expect(within(rows[0]).getByText("8,450")).toBeInTheDocument();
    expect(within(rows[4]).getByText("John")).toBeInTheDocument();

    // The whole field, unlike anything a participant is shown — this is the
    // host's administrative record.
    expect(screen.getByText("David")).toBeInTheDocument();
  });

  it("renders the server's ranks verbatim rather than re-deriving them", () => {
    // A hypothetical ranking that shares a rank, and one that does not start
    // at 1. Both are the server's business; re-sorting or renumbering here
    // would defeat the point of having stored them.
    render(
      <FinalStandingsTable
        standings={standings({
          entries: [
            { participantId: "p1", displayName: "Ada", rank: 2, score: 500 },
            { participantId: "p2", displayName: "Grace", rank: 2, score: 500 },
            { participantId: "p3", displayName: "Alan", rank: 4, score: 100 }
          ]
        })}
      />
    );

    const rows = screen.getAllByRole("row").slice(1);
    expect(within(rows[0]).getByText("2")).toBeInTheDocument();
    expect(within(rows[1]).getByText("2")).toBeInTheDocument();
    expect(within(rows[2]).getByText("4")).toBeInTheDocument();
    // Order as received, not sorted by score or name.
    expect(within(rows[0]).getByText("Ada")).toBeInTheDocument();
    expect(within(rows[2]).getByText("Alan")).toBeInTheDocument();
  });

  it("says so plainly when nothing was recorded", () => {
    // Sessions that finished before this history existed have no rows. An
    // empty table reads as a loading state that never resolved.
    render(<FinalStandingsTable standings={standings({ entries: [], capturedAt: undefined })} />);

    expect(screen.getByText(/no standings were recorded/i)).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("truncates a long name instead of pushing the score out of the table", () => {
    render(
      <FinalStandingsTable
        standings={standings({
          entries: [
            {
              participantId: "p1",
              displayName: "Bartholomew Fitzwilliam-Harrington the Third",
              rank: 1,
              score: 8450
            }
          ]
        })}
      />
    );

    const name = screen.getByText("Bartholomew Fitzwilliam-Harrington the Third");
    // max-w-0 with table-fixed is what actually lets a table cell shrink
    // enough to ellipsis; the full name stays reachable on hover.
    expect(name).toHaveClass("truncate", "max-w-0");
    expect(name).toHaveAttribute("title", "Bartholomew Fitzwilliam-Harrington the Third");
    expect(screen.getByText("8,450")).toBeInTheDocument();
  });
});
