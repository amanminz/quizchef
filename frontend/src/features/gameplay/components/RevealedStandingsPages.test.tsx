import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { RevealedStandingsPages } from "@/features/gameplay/components/RevealedStandingsPages";
import { leaderboardEntry } from "@/test/gameplayFixtures";

function standings(from: number, to: number) {
  return Array.from({ length: to - from + 1 }, (_, index) =>
    leaderboardEntry({
      displayName: `Player ${from + index}`,
      rank: from + index,
      score: 1000 - (from + index)
    })
  );
}

describe("RevealedStandingsPages", () => {
  it("renders exactly the rows it is given, in order", () => {
    render(<RevealedStandingsPages entries={standings(4, 10)} />);

    const rows = screen.getAllByRole("listitem");
    expect(rows).toHaveLength(7);
    expect(within(rows[0]).getByText("Player 4")).toBeInTheDocument();
    expect(within(rows[6]).getByText("Player 10")).toBeInTheDocument();
    expect(screen.getByText("Ranks 4–10")).toBeInTheDocument();
    // One page: no controls to distract the host with.
    expect(screen.queryByRole("button", { name: /next/i })).not.toBeInTheDocument();
  });

  it("pages a long group instead of scrolling it", async () => {
    const user = userEvent.setup();
    render(<RevealedStandingsPages entries={standings(4, 25)} />);

    // Fixed page size, so the layout never reflows between pages — and no
    // scroll container, because nobody can scroll a projector.
    expect(screen.getAllByRole("listitem")).toHaveLength(10);
    expect(screen.getByText("Ranks 4–13")).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /previous/i })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: /next/i }));
    expect(screen.getByText("Ranks 14–23")).toBeInTheDocument();
    expect(screen.queryByText("Player 4")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /next/i }));
    expect(screen.getByText("Ranks 24–25")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /next/i })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: /previous/i }));
    expect(screen.getByText("Ranks 14–23")).toBeInTheDocument();
  });

  it("is navigable from the keyboard", async () => {
    const user = userEvent.setup();
    render(<RevealedStandingsPages entries={standings(4, 25)} />);

    // Previous is disabled on page one, so it is not in the tab order at
    // all — one tab lands on Next.
    await user.tab();
    expect(screen.getByRole("button", { name: /next/i })).toHaveFocus();
    await user.keyboard("{Enter}");
    expect(screen.getByText("Ranks 14–23")).toBeInTheDocument();
  });

  it("renders nothing at all when the reveal group stops at the podium", () => {
    const { container } = render(<RevealedStandingsPages entries={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
