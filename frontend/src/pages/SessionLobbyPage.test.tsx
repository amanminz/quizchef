import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import { apiError, testIdentity } from "@/test/handlers";
import { quizResponse } from "@/test/quizFixtures";
import { sessionSummary } from "@/test/sessionFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";
import type { SessionSummaryResponse } from "@/types/api";

/**
 * Lobby tests drive the realtime side through the fake STOMP transport
 * (PR #1's seam): the MSW handler reads a mutable session holder, so a
 * delivered event followed by the query invalidation refetches whatever
 * "the server" now says — the same event-then-reconcile shape production
 * has.
 */
function setupLobby(overrides: Partial<SessionSummaryResponse> = {}) {
  useAuthStore.setState({ token: testIdentity.token });
  const holder = { session: sessionSummary({ state: "LOBBY", ...overrides }) };
  server.use(
    http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
      HttpResponse.json(holder.session)
    ),
    http.get(`/api/v1/quizzes/${holder.session.publishedQuizVersionId}`, () =>
      HttpResponse.json(
        quizResponse({
          id: holder.session.publishedQuizVersionId,
          state: "PUBLISHED",
          localizations: [{ languageCode: "en", title: "Bible Quiz" }]
        })
      )
    )
  );
  const { client, fake } = fakeRealtimeClient();
  renderApp(`/sessions/${holder.session.sessionId}/lobby`, { realtimeClient: client });
  return { holder, fake };
}

describe("SessionLobbyPage", () => {
  it("renders the lobby from the session summary and connects realtime", async () => {
    const { holder, fake } = setupLobby();

    expect(await screen.findByText(holder.session.sessionPin!)).toBeInTheDocument();
    expect(screen.getByText("Participants (0)")).toBeInTheDocument();
    expect(screen.getByText(/waiting for participants/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /start session/i })).toBeDisabled();

    act(() => fake.simulateConnect());
    expect(await screen.findByText("Live")).toBeInTheDocument();
    // The lobby subscribed to its session topic through SessionSubscriptions.
    expect(fake.subscriptions.has(sessionTopic(holder.session.sessionId!))).toBe(true);
  });

  it("adds joining participants to the roster and refreshes the server count", async () => {
    const { holder, fake } = setupLobby();
    await screen.findByText("Participants (0)");
    act(() => fake.simulateConnect());

    holder.session = { ...holder.session, participantCount: 1 };
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("participant.joined", holder.session.sessionId!, {
          participantId: "p-42cafe99"
        })
      );
    });

    expect(await screen.findByText("Participants (1)")).toBeInTheDocument();
    expect(screen.getByText(/player p-42cafe/i)).toBeInTheDocument();
    expect(screen.getByText("Connected")).toBeInTheDocument();
    // One participant in LOBBY: the server-reported state is startable.
    expect(screen.getByRole("button", { name: /start session/i })).toBeEnabled();
  });

  it("dims a disconnected participant instead of removing them", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 });
    await screen.findByText("Participants (1)");
    act(() => fake.simulateConnect());

    const topic = sessionTopic(holder.session.sessionId!);
    act(() => {
      fake.deliver(
        topic,
        protocolMessage("participant.joined", holder.session.sessionId!, {
          participantId: "p-durable"
        })
      );
    });
    await screen.findByText("Connected");

    act(() => {
      fake.deliver(
        topic,
        protocolMessage("participant.disconnected", holder.session.sessionId!, {
          participantId: "p-durable"
        })
      );
    });

    expect(await screen.findByText("Disconnected")).toBeInTheDocument();
    // The roster shows the id's first 8 characters.
    expect(screen.getByText(/player p-durabl/i)).toBeInTheDocument();
  });

  it("shows participants who joined before this view subscribed as a count row", async () => {
    setupLobby({ participantCount: 2 });

    expect(await screen.findByText("Participants (2)")).toBeInTheDocument();
    expect(screen.getByText(/2 joined before this view opened/i)).toBeInTheDocument();
  });

  it("shows a banner while the realtime connection is lost and clears it on recovery", async () => {
    const { fake } = setupLobby();
    await screen.findByText("Participants (0)");

    act(() => fake.simulateConnect());
    await waitFor(() => {
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    act(() => fake.simulateConnectionLost());
    expect(await screen.findByRole("alert")).toHaveTextContent(/connection lost/i);

    act(() => fake.simulateConnect());
    await waitFor(() => {
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
  });

  it("starts the session only through the server and navigates on confirmation", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 });
    server.use(
      http.post(`/api/v1/sessions/${holder.session.sessionId}/start`, () => {
        holder.session = { ...holder.session, state: "IN_PROGRESS" };
        return HttpResponse.json(holder.session);
      })
    );
    await screen.findByText("Participants (1)");
    act(() => fake.simulateConnect());
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /start session/i }));

    await waitFor(() => {
      expect(currentPath()).toBe(`/sessions/${holder.session.sessionId}/play`);
    });
    // No question has opened yet — the gameplay page's countdown state.
    expect(await screen.findByText(/get ready/i)).toBeInTheDocument();
  });

  it("stays in the lobby and shows the error when the server refuses the start", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 });
    server.use(
      http.post(`/api/v1/sessions/${holder.session.sessionId}/start`, () =>
        HttpResponse.json(apiError("auth.forbidden", "You are not the host"), { status: 403 })
      )
    );
    await screen.findByText("Participants (1)");
    act(() => fake.simulateConnect());
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /start session/i }));

    expect(await screen.findByText(/you are not the host/i)).toBeInTheDocument();
    expect(currentPath()).toBe(`/sessions/${holder.session.sessionId}/lobby`);
  });

  it("follows a session.started broadcast into the live route", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 });
    await screen.findByText("Participants (1)");
    act(() => fake.simulateConnect());

    holder.session = { ...holder.session, state: "IN_PROGRESS" };
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("session.started", holder.session.sessionId!)
      );
    });

    await waitFor(() => {
      expect(currentPath()).toBe(`/sessions/${holder.session.sessionId}/play`);
    });
  });

  it("offers to open the lobby when the session is still scheduled", async () => {
    const { holder } = setupLobby({ state: "CREATED" });
    server.use(
      http.post(`/api/v1/sessions/${holder.session.sessionPin}/lobby`, () => {
        holder.session = { ...holder.session, state: "LOBBY" };
        return HttpResponse.json(holder.session);
      })
    );
    const user = userEvent.setup();

    expect(await screen.findByText(/the lobby is not open yet/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /open lobby/i }));

    expect(await screen.findByText("Participants (0)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /start session/i })).toBeInTheDocument();
  });
});
