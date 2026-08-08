import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
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

function serve(session: SessionSummaryResponse) {
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

describe("SessionDetailsPage", () => {
  it("renders the session's summary, code, and server-assigned settings", async () => {
    signIn();
    const session = sessionSummary({ state: "CREATED", participantCount: 0 });
    serve(session);

    renderApp(`/sessions/${session.sessionId}`);

    expect(await screen.findByText(session.sessionPin!)).toBeInTheDocument();
    expect(screen.getByText("Scheduled")).toBeInTheDocument();
    // The host is the signed-in identity.
    expect(screen.getByText("You")).toBeInTheDocument();
    // Server-assigned settings are read-only display.
    expect(screen.getByText("Max participants")).toBeInTheDocument();
    expect(screen.getByText("100")).toBeInTheDocument();
    expect(screen.getByText("Late join")).toBeInTheDocument();
  });

  it("shows a finished session's captured standings", async () => {
    // This is the page a host opens from the sessions list to look back at
    // an event, so it is where the history has to be — not only on the
    // gameplay screen they have already navigated away from.
    signIn();
    const session = sessionSummary({ state: "FINISHED", participantCount: 3 });
    serve(session);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/final-standings`, () =>
        HttpResponse.json({
          sessionId: session.sessionId,
          capturedAt: "2026-08-08T18:00:00Z",
          entries: [
            { participantId: "p1", displayName: "Amelia", rank: 1, score: 8450 },
            { participantId: "p2", displayName: "Aman", rank: 2, score: 8120 },
            { participantId: "p3", displayName: "Ruth", rank: 3, score: 7780 }
          ]
        })
      )
    );

    renderApp(`/sessions/${session.sessionId}`);

    const table = await screen.findByRole("table", { name: /final standings/i });
    expect(within(table).getByText("Amelia")).toBeInTheDocument();
    expect(within(table).getByText("Ruth")).toBeInTheDocument();
    expect(within(table).getByText("8,450")).toBeInTheDocument();
  });

  it("asks for no standings on a session that has not finished", async () => {
    // Nothing is recorded until a session ends, so requesting it would only
    // earn an empty list that looks like a fault.
    signIn();
    const session = sessionSummary({ state: "LOBBY" });
    serve(session);
    let requested = false;
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/final-standings`, () => {
        requested = true;
        return HttpResponse.json({ sessionId: session.sessionId, entries: [] });
      })
    );

    renderApp(`/sessions/${session.sessionId}`);

    expect(await screen.findByText(session.sessionPin!)).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: /final standings/i })).not.toBeInTheDocument();
    await waitFor(() => expect(requested).toBe(false));
  });

  it("opens the lobby and navigates into it", async () => {
    signIn();
    const session = sessionSummary({ state: "CREATED" });
    serve(session);
    server.use(
      http.post(`/api/v1/sessions/${session.sessionPin}/lobby`, () =>
        HttpResponse.json({ ...session, state: "LOBBY" })
      )
    );
    const user = userEvent.setup();
    const { client } = fakeRealtimeClient();

    renderApp(`/sessions/${session.sessionId}`, { realtimeClient: client });
    await user.click(await screen.findByRole("button", { name: /open lobby/i }));

    await waitFor(() => {
      expect(currentPath()).toBe(`/sessions/${session.sessionId}/lobby`);
    });
  });

  it("keeps the host on the page and shows the error when opening the lobby is rejected", async () => {
    signIn();
    const session = sessionSummary({ state: "CREATED" });
    serve(session);
    server.use(
      http.post(`/api/v1/sessions/${session.sessionPin}/lobby`, () =>
        HttpResponse.json(apiError("auth.forbidden", "You are not the host"), { status: 403 })
      )
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}`);
    await user.click(await screen.findByRole("button", { name: /open lobby/i }));

    expect(await screen.findByText(/you are not the host/i)).toBeInTheDocument();
    expect(currentPath()).toBe(`/sessions/${session.sessionId}`);
  });
});
