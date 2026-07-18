import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { usePresentationStore } from "@/features/sessions/presentationStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import { apiError, testIdentity } from "@/test/handlers";
import { quizResponse } from "@/test/quizFixtures";
import { sessionSummary } from "@/test/sessionFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";
import type { SessionParticipantDto, SessionSummaryResponse } from "@/types/api";

/**
 * Lobby tests drive the realtime side through the fake STOMP transport
 * (PR #1's seam). The roster read is a mutable holder too: a delivered
 * participant event invalidates the roster query, which refetches
 * whatever "the server" now says — names ride the read, never the event.
 */
function setupLobby(
  overrides: Partial<SessionSummaryResponse> = {},
  roster: SessionParticipantDto[] = []
) {
  useAuthStore.setState({ token: testIdentity.token });
  const holder = {
    session: sessionSummary({ state: "LOBBY", ...overrides }),
    roster
  };
  server.use(
    http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
      HttpResponse.json(holder.session)
    ),
    http.get(`/api/v1/sessions/${holder.session.sessionId}/participants`, () =>
      HttpResponse.json({
        sessionId: holder.session.sessionId,
        participantCount: holder.roster.length,
        participants: holder.roster
      })
    ),
    http.get(`/api/v1/quizzes/${holder.session.publishedQuizVersionId}`, () =>
      HttpResponse.json(
        quizResponse({
          id: holder.session.publishedQuizVersionId,
          state: "PUBLISHED",
          questionIds: ["q-1", "q-2"],
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
  beforeEach(() => {
    usePresentationStore.setState({ active: false });
  });

  it("renders the lobby with the player wall and readiness panel", async () => {
    const { holder, fake } = setupLobby();

    expect(await screen.findByText(holder.session.sessionPin!)).toBeInTheDocument();
    expect(screen.getByText("Players joined")).toBeInTheDocument();
    expect(screen.getByTestId("player-count")).toHaveTextContent("0");
    expect(screen.getByText(/waiting for participants/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /start session/i })).toBeDisabled();
    // Pre-flight readiness: quiz and question count from the cached quiz read.
    expect(await screen.findByText("Quiz ready")).toBeInTheDocument();
    expect(screen.getByText("2 questions")).toBeInTheDocument();
    expect(screen.getByText("0 players joined")).toBeInTheDocument();

    act(() => fake.simulateConnect());
    expect(await screen.findByText("Realtime connected")).toBeInTheDocument();
    expect(fake.subscriptions.has(sessionTopic(holder.session.sessionId!))).toBe(true);
  });

  it("shows every joined name on the wall when the roster event arrives", async () => {
    const { holder, fake } = setupLobby();
    await screen.findByText("Players joined");
    act(() => fake.simulateConnect());

    // The event carries only an id; the name arrives via the roster read.
    holder.session = { ...holder.session, participantCount: 1 };
    holder.roster = [{ participantId: "p-1", displayName: "Amelia", connected: true }];
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("participant.joined", holder.session.sessionId!, {
          participantId: "p-1"
        })
      );
    });

    await waitFor(() => expect(screen.getByTestId("player-count")).toHaveTextContent("1"));
    expect(await screen.findByText("Amelia")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /start session/i })).toBeEnabled();
  });

  it("keeps a disconnected participant on the wall, dimmed not removed", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 }, [
      { participantId: "p-1", displayName: "Ruth", connected: true }
    ]);
    expect(await screen.findByText("Ruth")).toBeInTheDocument();
    act(() => fake.simulateConnect());

    holder.roster = [{ participantId: "p-1", displayName: "Ruth", connected: false }];
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("participant.disconnected", holder.session.sessionId!, {
          participantId: "p-1"
        })
      );
    });

    // Backend semantics: disconnected participants remain on the roster.
    await waitFor(() => {
      expect(screen.getByText("Ruth").closest("li")).toHaveClass("opacity-50");
    });
  });

  it("renders an overflow row when the count outruns the roster read", async () => {
    setupLobby({ participantCount: 5 }, [
      { participantId: "p-1", displayName: "Amelia", connected: true },
      { participantId: "p-2", displayName: "Ruth", connected: true }
    ]);

    expect(await screen.findByTestId("player-count")).toHaveTextContent("5");
    expect(await screen.findByText("Amelia")).toBeInTheDocument();
    expect(screen.getByText("Ruth")).toBeInTheDocument();
    expect(screen.getByText("+3 more")).toBeInTheDocument();
  });

  it("shows the lost banner while disconnected and confirms recovery", async () => {
    const { fake } = setupLobby();
    await screen.findByText("Players joined");

    act(() => fake.simulateConnect());
    await waitFor(() => {
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    act(() => fake.simulateConnectionLost());
    expect(await screen.findByRole("alert")).toHaveTextContent(/connection lost/i);

    act(() => fake.simulateConnect());
    expect(await screen.findByText("Connection restored.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("starts only after an explicit confirmation showing the late-join setting", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 }, [
      { participantId: "p-1", displayName: "Amelia", connected: true }
    ]);
    let started = false;
    server.use(
      http.post(`/api/v1/sessions/${holder.session.sessionId}/start`, () => {
        started = true;
        holder.session = { ...holder.session, state: "IN_PROGRESS" };
        return HttpResponse.json(holder.session);
      })
    );
    await screen.findByText("Amelia");
    act(() => fake.simulateConnect());
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /start session/i }));

    // The click opened the confirmation — nothing was sent yet.
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Start the quiz for 1 player?");
    expect(dialog).toHaveTextContent(/late join is disabled/i);
    expect(started).toBe(false);

    await user.click(within(dialog).getByRole("button", { name: /start session/i }));

    await waitFor(() => {
      expect(currentPath()).toBe(`/sessions/${holder.session.sessionId}/play`);
    });
    expect(started).toBe(true);
  });

  it("stays in the lobby and shows the error when the server refuses the start", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 }, [
      { participantId: "p-1", displayName: "Amelia", connected: true }
    ]);
    server.use(
      http.post(`/api/v1/sessions/${holder.session.sessionId}/start`, () =>
        HttpResponse.json(apiError("auth.forbidden", "You are not the host"), { status: 403 })
      )
    );
    await screen.findByText("Amelia");
    act(() => fake.simulateConnect());
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /start session/i }));
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", { name: /start session/i })
    );

    expect(await screen.findByText(/you are not the host/i)).toBeInTheDocument();
    expect(currentPath()).toBe(`/sessions/${holder.session.sessionId}/lobby`);
  });

  it("follows a session.started broadcast into the live route", async () => {
    const { holder, fake } = setupLobby({ participantCount: 1 });
    await screen.findByText("Players joined");
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

    expect(await screen.findByText("Players joined")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /start session/i })).toBeInTheDocument();
  });

  it("presentation mode strips the chrome and falls back without fullscreen", async () => {
    // Deterministic denial: the layout must work even when the browser
    // refuses the fullscreen request.
    const originalRequestFullscreen = HTMLElement.prototype.requestFullscreen;
    HTMLElement.prototype.requestFullscreen = () => Promise.reject(new Error("denied"));
    setupLobby();
    await screen.findByText("Players joined");
    // The authenticated chrome is present before presenting.
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /enter presentation mode/i }));

    // Chrome gone, session content still on screen.
    await waitFor(() => {
      expect(screen.queryByRole("link", { name: "Dashboard" })).not.toBeInTheDocument();
    });
    expect(screen.queryByRole("link", { name: "Question Library" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /log out/i })).not.toBeInTheDocument();
    expect(screen.getByText("Players joined")).toBeInTheDocument();
    // The browser refused fullscreen: the layout still applies, with the hint.
    expect(await screen.findByText(/use your browser's fullscreen shortcut/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /exit presentation mode/i }));
    expect(await screen.findByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    HTMLElement.prototype.requestFullscreen = originalRequestFullscreen;
  });
});
