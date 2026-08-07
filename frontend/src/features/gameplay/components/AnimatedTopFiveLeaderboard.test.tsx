import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AnimatedTopFiveLeaderboard } from "@/features/gameplay/components/AnimatedTopFiveLeaderboard";
import { topFiveTransitionResponse } from "@/test/gameplayFixtures";
import type { TopFiveLeaderboardTransitionResponse } from "@/types/api";

/**
 * The animation is driven by timers, so every case here steps them
 * explicitly rather than waiting out a real second — the sequence is
 * asserted, not raced.
 */
function advance(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
}

/** The count-up, then the reorder, then settled. */
const SCORE_PHASE_MS = 1000;
const MOVE_PHASE_MS = 700;

function withReducedMotion(reduced: boolean) {
  window.matchMedia = (query: string) =>
    ({
      matches: reduced && query.includes("prefers-reduced-motion"),
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false
    }) as MediaQueryList;
}

/**
 * The score cell of a named row. Selected by the monospace class rather
 * than by position: the row is a grid of rank, name, score, and movement,
 * several of which are numeric, and "the last number in the row" quietly
 * followed the layout around.
 */
function scoreOf(displayName: string): string {
  const row = screen.getByText(displayName).closest("li");
  if (!row) {
    throw new Error(`${displayName} is not on the board`);
  }
  const score = row.querySelector(".font-mono");
  if (!score) {
    throw new Error(`${displayName}'s row has no score cell`);
  }
  return score.textContent ?? "";
}

function renderBoard(
  transition: TopFiveLeaderboardTransitionResponse = topFiveTransitionResponse(),
  presentationActive = false
) {
  const onComplete = vi.fn();
  render(
    <AnimatedTopFiveLeaderboard
      transition={transition}
      presentationActive={presentationActive}
      onComplete={onComplete}
    />
  );
  return { onComplete };
}

/** The grid cell holding a row's name. */
function nameCellOf(displayName: string): HTMLElement {
  const cell = screen.getByText(displayName);
  return cell;
}

describe("AnimatedTopFiveLeaderboard", () => {
  afterEach(() => {
    vi.useRealTimers();
    withReducedMotion(false);
  });

  it("counts each score from the server's previous total to its current one", () => {
    vi.useFakeTimers();
    renderBoard();

    // Before a single frame: everyone stands at what they had, not at zero
    // and not at their new total.
    expect(scoreOf("Fran")).toBe("400");
    expect(scoreOf("Ann")).toBe("900");

    // Mid-count, the mover is somewhere between the two server values —
    // never past the one it is heading for.
    advance(SCORE_PHASE_MS / 2);
    const midway = Number(scoreOf("Fran").replace(/,/g, ""));
    expect(midway).toBeGreaterThan(400);
    expect(midway).toBeLessThan(1400);

    // And it lands on exactly the server's number, not near it.
    advance(SCORE_PHASE_MS);
    expect(scoreOf("Fran")).toBe("1,400");
    expect(scoreOf("Ann")).toBe("900");
  });

  it("shows the points the question awarded", () => {
    vi.useFakeTimers();
    renderBoard();

    expect(screen.getByText("+1000")).toBeInTheDocument();
    // Nobody else scored, and a zero is not dressed up as a gain.
    expect(screen.queryByText("+0")).not.toBeInTheDocument();
  });

  it("holds the previous order until the scores have finished counting", () => {
    vi.useFakeTimers();
    renderBoard();

    // During the count-up, rows show the ranks they *held*: Ann is still
    // first, and no movement is claimed yet.
    const ann = screen.getByText("Ann").closest("li")!;
    expect(within(ann).getByText("1")).toBeInTheDocument();
    expect(screen.queryByLabelText(/new in the top 5/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/down 1 place/i)).not.toBeInTheDocument();

    // Only once the counting is done does the board reorder and explain
    // itself.
    advance(SCORE_PHASE_MS);
    const annSettled = screen.getByText("Ann").closest("li")!;
    expect(within(annSettled).getByText("2")).toBeInTheDocument();
    expect(within(annSettled).getByLabelText("Down 1 place")).toBeInTheDocument();
  });

  it("labels a row arriving from outside the board as new, never as a distance", () => {
    vi.useFakeTimers();
    renderBoard();
    advance(SCORE_PHASE_MS);

    const fran = screen.getByText("Fran").closest("li")!;
    expect(within(fran).getByLabelText("New in the top 5")).toBeInTheDocument();
    expect(within(fran).getByText("New Top 5")).toBeInTheDocument();
  });

  it("states movement in words and numbers, never by colour alone", () => {
    vi.useFakeTimers();
    const transition = topFiveTransitionResponse();
    // A genuine two-place climb: third before, first now.
    transition.currentTopFive = [
      {
        participantId: "participant-cara",
        displayName: "Cara",
        previousRank: 3,
        currentRank: 1,
        previousScore: 700,
        currentScore: 1500,
        pointsEarned: 800
      },
      ...transition.currentTopFive!.filter((entry) => entry.participantId !== "participant-cara")
        .slice(0, 4)
    ];
    renderBoard(transition);
    advance(SCORE_PHASE_MS);

    const cara = screen.getByText("Cara").closest("li")!;
    expect(within(cara).getByLabelText("Up 2 places")).toBeInTheDocument();
    // The figure is rendered as text, so the arrow's colour is never the
    // only thing carrying the meaning.
    expect(within(cara).getByText("2")).toBeInTheDocument();
  });

  it("leaves the departing row out of the settled board", () => {
    vi.useFakeTimers();
    renderBoard();

    // Erin is on the board while her score counts — the room watches her
    // get overtaken rather than vanish mid-sentence.
    expect(screen.getByText("Erin")).toBeInTheDocument();

    advance(SCORE_PHASE_MS + MOVE_PHASE_MS);

    // Once settled she is no longer part of the top five: her row is
    // marked decorative, and her position below fifth is never stated.
    expect(screen.getByText("Erin").closest("li")).toHaveAttribute("aria-hidden", "true");
    const erin = screen.getByText("Erin").closest("li")!;
    expect(within(erin).queryByLabelText(/down \d+ place/i)).not.toBeInTheDocument();
  });

  it("keeps one row per participant across the whole sequence", () => {
    vi.useFakeTimers();
    renderBoard();

    const before = screen.getByText("Ann").closest("li");
    advance(SCORE_PHASE_MS + MOVE_PHASE_MS);

    // The same element throughout: rows are keyed by participant id, so a
    // rank change moves a row rather than destroying and rebuilding one.
    expect(screen.getByText("Ann").closest("li")).toBe(before);
  });

  it("skips to the authoritative final board without touching the server", async () => {
    const user = userEvent.setup();
    const { onComplete } = renderBoard();

    await user.click(screen.getByRole("button", { name: /skip animation/i }));

    // Final scores, final order, final labels — immediately.
    expect(scoreOf("Fran")).toBe("1,400");
    expect(within(screen.getByText("Ann").closest("li")!).getByText("2")).toBeInTheDocument();
    expect(screen.getByLabelText("New in the top 5")).toBeInTheDocument();
    expect(onComplete).toHaveBeenCalled();
    // Nothing left to skip.
    expect(screen.queryByRole("button", { name: /skip animation/i })).not.toBeInTheDocument();
  });

  it("is skippable from the keyboard", async () => {
    const user = userEvent.setup();
    renderBoard();

    await user.tab();
    expect(screen.getByRole("button", { name: /skip animation/i })).toHaveFocus();
    await user.keyboard("{Enter}");

    expect(scoreOf("Fran")).toBe("1,400");
  });

  it("renders the settled board at once under prefers-reduced-motion", () => {
    withReducedMotion(true);
    vi.useFakeTimers();
    const { onComplete } = renderBoard();

    // No count-up and no travel — but the same numbers, the same order,
    // and the same explanation of what moved.
    expect(scoreOf("Fran")).toBe("1,400");
    const ann = screen.getByText("Ann").closest("li")!;
    expect(within(ann).getByText("2")).toBeInTheDocument();
    expect(within(ann).getByLabelText("Down 1 place")).toBeInTheDocument();
    expect(screen.getByLabelText("New in the top 5")).toBeInTheDocument();
    expect(onComplete).toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: /skip animation/i })).not.toBeInTheDocument();
  });

  it("renders equal scores at the distinct ranks the server assigned", () => {
    vi.useFakeTimers();
    const transition = topFiveTransitionResponse({
      previousTopFive: [],
      currentTopFive: [
        {
          participantId: "participant-amelia",
          displayName: "Amelia",
          currentRank: 1,
          previousScore: 0,
          currentScore: 900,
          pointsEarned: 900
        },
        {
          participantId: "participant-aman",
          displayName: "Aman",
          currentRank: 2,
          previousScore: 0,
          currentScore: 900,
          pointsEarned: 900
        }
      ]
    });
    renderBoard(transition);
    advance(SCORE_PHASE_MS + MOVE_PHASE_MS);

    // Two equal scores, two distinct ranks — the ranking service broke the
    // tie and the board reports what it decided.
    expect(within(screen.getByText("Amelia").closest("li")!).getByText("1")).toBeInTheDocument();
    expect(within(screen.getByText("Aman").closest("li")!).getByText("2")).toBeInTheDocument();
    expect(scoreOf("Amelia")).toBe("900");
    expect(scoreOf("Aman")).toBe("900");
  });

  it("sizes ranks, names, and scores for a projector, not a phone", () => {
    vi.useFakeTimers();
    renderBoard(topFiveTransitionResponse(), true);

    const row = screen.getByText("Ann").closest("li")!;

    // Four reserved columns: rank, name, score, movement. The name column is
    // minmax(0, 1fr) so it shrinks instead of pushing the numbers off screen
    // — a grid child would otherwise refuse to go below its content width.
    expect(row.getAttribute("style")).toContain("minmax(0, 1fr)");

    // Projector-scale type on everything that carries meaning.
    expect(nameCellOf("Ann").getAttribute("style")).toContain("clamp(1.6rem, 2.6vw, 3.1rem)");
    expect(row.querySelector(".font-mono")!.getAttribute("style"))
        .toContain("clamp(1.6rem, 2.6vw, 3.1rem)");
    expect(within(row).getByText("1").getAttribute("style"))
        .toContain("clamp(1.75rem, 3vw, 3.5rem)");

    // Numerals that do not jitter while a score counts up.
    expect(row.querySelector(".font-mono")).toHaveClass("tabular-nums");
  });

  it("truncates a long name rather than shrinking the row or losing the score", () => {
    vi.useFakeTimers();
    const transition = topFiveTransitionResponse();
    transition.currentTopFive![1] = {
      ...transition.currentTopFive![1],
      displayName: "Bartholomew Fitzwilliam-Harrington the Third"
    };
    renderBoard(transition, true);
    advance(SCORE_PHASE_MS + MOVE_PHASE_MS);

    const name = nameCellOf("Bartholomew Fitzwilliam-Harrington the Third");
    // min-w-0 plus truncate is what actually engages ellipsis inside a grid
    // column; without min-w-0 the name wins and the score leaves the screen.
    expect(name).toHaveClass("truncate", "min-w-0");
    // The full name stays available even though it is visually clipped.
    expect(name).toHaveAttribute("title", "Bartholomew Fitzwilliam-Harrington the Third");
    // And its row still shows a score and a movement indicator.
    const row = name.closest("li")!;
    expect(row.querySelector(".font-mono")!.textContent).toBe("900");
    expect(within(row).getByLabelText(/down 1 place/i)).toBeInTheDocument();
  });

  it("never renders more than five rows", () => {
    vi.useFakeTimers();
    renderBoard(topFiveTransitionResponse(), true);
    advance(SCORE_PHASE_MS + MOVE_PHASE_MS);

    // The board is the current Top 5 plus whoever is on their way out; the
    // leaver is decorative and gone from the settled list.
    const visible = screen
      .getAllByRole("listitem")
      .filter((row) => row.getAttribute("aria-hidden") !== "true");
    expect(visible).toHaveLength(5);
  });

  it("shows only the players that exist in a room smaller than five", () => {
    vi.useFakeTimers();
    const full = topFiveTransitionResponse();
    const transition = topFiveTransitionResponse({
      previousTopFive: full.previousTopFive!.slice(0, 2),
      currentTopFive: full.currentTopFive!.slice(1, 3)
    });
    renderBoard(transition);
    advance(SCORE_PHASE_MS + MOVE_PHASE_MS);

    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.queryByText("Dave")).not.toBeInTheDocument();
  });
});
