import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { SessionQuestionsPanel } from "@/features/sessions/components/SessionQuestionsPanel";
import { server } from "@/test/server";
import type { SessionQuestionsResponse } from "@/types/api";

const OPTION_A = "11111111-1111-1111-1111-111111111111";
const OPTION_B = "22222222-2222-2222-2222-222222222222";

function questions(): SessionQuestionsResponse {
  return {
    sessionId: "session-1",
    totalQuestions: 3,
    questions: [
      question("q1", 1, "PLAYED", "Who built the ark?"),
      question("q2", 2, "CURRENT", "Who parted the Red Sea?"),
      question("q3", null, "REMOVED", "A question the host pulled"),
      question("q4", 3, "UPCOMING", "Who wrote most of the Psalms?")
    ]
  };
}

function question(
  questionId: string,
  questionNumber: number | null,
  status: "PLAYED" | "CURRENT" | "UPCOMING" | "REMOVED",
  prompt: string
) {
  return {
    questionId,
    questionNumber,
    status,
    corrected: false,
    questionType: "SINGLE_CHOICE" as const,
    defaultLanguage: "en",
    correctOptionIds: [OPTION_A],
    options: [
      { optionId: OPTION_A, displayOrder: 1 },
      { optionId: OPTION_B, displayOrder: 2 }
    ],
    localizations: [
      {
        languageCode: "en",
        prompt,
        explanation: null,
        optionTexts: [
          { optionId: OPTION_A, text: "Noah" },
          { optionId: OPTION_B, text: "Moses" }
        ]
      }
    ]
  };
}

function renderPanel(answeredCount = 0) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return render(<SessionQuestionsPanel sessionId="session-1" answeredCount={answeredCount} />, {
    wrapper: Wrapper
  });
}

describe("SessionQuestionsPanel", () => {
  it("does not read the question list until the host opens it", async () => {
    let reads = 0;
    server.use(
      http.get("/api/v1/sessions/session-1/questions", () => {
        reads += 1;
        return HttpResponse.json(questions());
      })
    );

    renderPanel();

    // The read carries unrevealed answer keys, so it is not fetched into a
    // cache the host has not asked to look at.
    expect(reads).toBe(0);
    await userEvent.click(screen.getByRole("button", { name: /session questions/i }));
    await waitFor(() => expect(reads).toBe(1));
  });

  it("numbers the effective sequence and marks what the host removed", async () => {
    server.use(
      http.get("/api/v1/sessions/session-1/questions", () => HttpResponse.json(questions()))
    );

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: /session questions/i }));

    const rows = await screen.findAllByRole("listitem");
    expect(rows).toHaveLength(4);
    // Numbering comes from the server's effective sequence: the removed
    // question sits where it was but carries no number, and the question
    // after it is 3 rather than 4.
    expect(within(rows[2]).getByText("—")).toBeInTheDocument();
    expect(within(rows[2]).getByText("Removed")).toBeInTheDocument();
    expect(within(rows[3]).getByText("3")).toBeInTheDocument();
    expect(screen.getByText("3 in play")).toBeInTheDocument();
  });

  it("offers no action on a question the room has already played", async () => {
    server.use(
      http.get("/api/v1/sessions/session-1/questions", () => HttpResponse.json(questions()))
    );

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: /session questions/i }));
    const rows = await screen.findAllByRole("listitem");

    // Rescoring or dropping a played question would change a leaderboard
    // the room has already seen; the server refuses, so nothing is offered.
    expect(within(rows[0]).queryByRole("button")).not.toBeInTheDocument();
    expect(within(rows[2]).queryByRole("button")).not.toBeInTheDocument();
    expect(within(rows[1]).getAllByRole("button")).toHaveLength(2);
  });

  it("shows no answer key in the list itself", async () => {
    server.use(
      http.get("/api/v1/sessions/session-1/questions", () => HttpResponse.json(questions()))
    );

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: /session questions/i }));
    await screen.findAllByRole("listitem");

    // The rows are prompt-only. A host may project this screen, and the
    // key belongs behind the correction dialog they open on purpose.
    expect(screen.queryByText("Noah")).not.toBeInTheDocument();
    expect(screen.getByText("Who parted the Red Sea?")).toBeInTheDocument();
  });
});
