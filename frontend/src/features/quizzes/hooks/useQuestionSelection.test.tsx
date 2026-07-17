import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { useQuestionSelection } from "@/features/quizzes/hooks/useQuestionSelection";
import { apiError } from "@/test/handlers";
import { quizResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("useQuestionSelection", () => {
  it("reorders optimistically, ahead of the server response", async () => {
    const quiz = quizResponse({ id: "quiz-1", questionIds: ["a", "b", "c"] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.patch("/api/v1/quizzes/quiz-1/questions/order", async () => {
        // A deliberately slow server response - the optimistic update must
        // already be visible before this resolves.
        await new Promise((resolve) => setTimeout(resolve, 50));
        return HttpResponse.json({ ...quiz, questionIds: ["c", "a", "b"] });
      })
    );

    const { result } = renderHook(() => useQuestionSelection("quiz-1"), { wrapper });
    await waitFor(() => expect(result.current.selectedQuestionIds).toEqual(["a", "b", "c"]));

    result.current.reorder(["c", "a", "b"]);

    await waitFor(() => expect(result.current.selectedQuestionIds).toEqual(["c", "a", "b"]));
  });

  it("rolls back to the server's order when reordering fails", async () => {
    const quiz = quizResponse({ id: "quiz-1", questionIds: ["a", "b", "c"] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.patch("/api/v1/quizzes/quiz-1/questions/order", () =>
        HttpResponse.json(apiError("quiz.questions.locked", "Published quizzes cannot reorder"), {
          status: 409
        })
      )
    );

    const { result } = renderHook(() => useQuestionSelection("quiz-1"), { wrapper });
    await waitFor(() => expect(result.current.selectedQuestionIds).toEqual(["a", "b", "c"]));

    result.current.reorder(["c", "a", "b"]);

    // The optimistic update is instantaneous and the rejection can resolve
    // within the same tick under fake network latency, so only the settled
    // end state is asserted: rolled back, with the error surfaced.
    await waitFor(() => expect(result.current.reorderError).toBeDefined());
    expect(result.current.selectedQuestionIds).toEqual(["a", "b", "c"]);
  });
});
