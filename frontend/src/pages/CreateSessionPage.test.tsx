import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { useHostedSessionsStore } from "@/features/sessions/hostedSessionsStore";
import { apiError, testIdentity } from "@/test/handlers";
import { quizPage, quizResponse, quizSummary } from "@/test/quizFixtures";
import { sessionSummary } from "@/test/sessionFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

describe("CreateSessionPage", () => {
  it("offers only published quizzes to host", async () => {
    signIn();
    let requestedState: string | null = null;
    server.use(
      http.get("/api/v1/quizzes/mine", ({ request }) => {
        requestedState = new URL(request.url).searchParams.get("state");
        return HttpResponse.json(
          quizPage([quizSummary({ title: "Published Quiz", state: "PUBLISHED" })])
        );
      })
    );

    renderApp("/sessions/new");

    expect(await screen.findByRole("radio", { name: /published quiz/i })).toBeInTheDocument();
    expect(requestedState).toBe("PUBLISHED");
  });

  it("shows the selected quiz's metadata and creates a server-confirmed session", async () => {
    signIn();
    const quiz = quizSummary({ title: "Exodus Quiz", state: "PUBLISHED", questionCount: 6 });
    const session = sessionSummary({ publishedQuizVersionId: quiz.id });
    let createdWith: unknown;
    server.use(
      http.get("/api/v1/quizzes/mine", () => HttpResponse.json(quizPage([quiz]))),
      http.get(`/api/v1/quizzes/${quiz.id}`, () =>
        HttpResponse.json(
          quizResponse({
            id: quiz.id,
            state: "PUBLISHED",
            localizations: [{ languageCode: "en", title: "Exodus Quiz" }],
            questionIds: ["q1", "q2", "q3", "q4", "q5", "q6"]
          })
        )
      ),
      http.post("/api/v1/sessions", async ({ request }) => {
        createdWith = await request.json();
        return HttpResponse.json(session, { status: 201 });
      }),
      http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session))
    );
    const user = userEvent.setup();

    renderApp("/sessions/new");
    await user.click(await screen.findByRole("radio", { name: /exodus quiz/i }));

    // Metadata panel: question count and estimated duration (6 × 30s = 3 min).
    expect(await screen.findByText(/6 questions · about 3 min/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /create session/i }));

    await waitFor(() => {
      expect(currentPath()).toBe(`/sessions/${session.sessionId}`);
    });
    expect(createdWith).toEqual({ publishedQuizVersionId: quiz.id });
    expect(useHostedSessionsStore.getState().sessionIds).toContain(session.sessionId);
  });

  it("surfaces the server's rejection when the quiz is not publishable", async () => {
    signIn();
    const quiz = quizSummary({ title: "Just Unpublished", state: "PUBLISHED" });
    server.use(
      http.get("/api/v1/quizzes/mine", () => HttpResponse.json(quizPage([quiz]))),
      http.get(`/api/v1/quizzes/${quiz.id}`, () =>
        HttpResponse.json(quizResponse({ id: quiz.id, state: "PUBLISHED" }))
      ),
      http.post("/api/v1/sessions", () =>
        HttpResponse.json(apiError("quiz.not-published", "The quiz is not published"), {
          status: 409
        })
      )
    );
    const user = userEvent.setup();

    renderApp("/sessions/new");
    await user.click(await screen.findByRole("radio", { name: /just unpublished/i }));
    await user.click(screen.getByRole("button", { name: /create session/i }));

    expect(await screen.findByText(/the quiz is not published/i)).toBeInTheDocument();
    expect(currentPath()).toBe("/sessions/new");
  });
});
