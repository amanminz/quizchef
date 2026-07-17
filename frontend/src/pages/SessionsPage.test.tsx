import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { useHostedSessionsStore } from "@/features/sessions/hostedSessionsStore";
import { fakeRealtimeClient } from "@/test/fakeStomp";
import { apiError, testIdentity } from "@/test/handlers";
import { quizResponse } from "@/test/quizFixtures";
import { sessionSummary } from "@/test/sessionFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";
import type { SessionSummaryResponse } from "@/types/api";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

/** Registers a session in the local hosted registry and serves it over MSW. */
function serveSession(session: SessionSummaryResponse) {
  useHostedSessionsStore.setState((state) => ({
    sessionIds: [...state.sessionIds, session.sessionId!]
  }));
  server.use(
    http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
    http.get(`/api/v1/quizzes/${session.publishedQuizVersionId}`, () =>
      HttpResponse.json(
        quizResponse({
          id: session.publishedQuizVersionId,
          state: "PUBLISHED",
          localizations: [{ languageCode: "en", title: "Bible Quiz" }]
        })
      )
    )
  );
}

describe("SessionsPage", () => {
  it("sections hosted sessions by lifecycle state", async () => {
    signIn();
    serveSession(sessionSummary({ state: "LOBBY" }));
    serveSession(sessionSummary({ state: "IN_PROGRESS" }));
    serveSession(sessionSummary({ state: "FINISHED" }));

    renderApp("/sessions");

    expect(await screen.findByRole("heading", { name: "Waiting" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "In Progress" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Completed" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /enter lobby/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /resume/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /view summary/i })).toBeInTheDocument();
  });

  it("shows the empty state when this browser hosts no sessions", async () => {
    signIn();
    renderApp("/sessions");

    expect(await screen.findByText("No sessions yet")).toBeInTheDocument();
  });

  it("prunes sessions the server no longer knows", async () => {
    signIn();
    useHostedSessionsStore.setState({ sessionIds: ["gone-session"] });
    server.use(
      http.get("/api/v1/sessions/gone-session", () =>
        HttpResponse.json(apiError("session.not-found", "No such session"), { status: 404 })
      )
    );

    renderApp("/sessions");

    expect(await screen.findByText("No sessions yet")).toBeInTheDocument();
    await waitFor(() => {
      expect(useHostedSessionsStore.getState().sessionIds).toEqual([]);
    });
  });

  it("opens the lobby of a scheduled session and navigates into it", async () => {
    signIn();
    const created = sessionSummary({ state: "CREATED" });
    serveSession(created);
    server.use(
      http.post(`/api/v1/sessions/${created.sessionPin}/lobby`, () =>
        HttpResponse.json({ ...created, state: "LOBBY" })
      )
    );
    const user = userEvent.setup();
    const { client } = fakeRealtimeClient();

    renderApp("/sessions", { realtimeClient: client });
    await user.click(await screen.findByRole("button", { name: /open lobby/i }));

    await waitFor(() => {
      expect(currentPath()).toBe(`/sessions/${created.sessionId}/lobby`);
    });
  });
});
