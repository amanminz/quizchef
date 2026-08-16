import { beforeEach, describe, expect, it } from "vitest";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";

const PIN = "042317";

function record(sessionId: string, resumeToken: string) {
  usePlayerSessionStore.getState().record(PIN, {
    sessionId,
    participantId: `participant-for-${sessionId}`,
    resumeToken,
    displayName: "Aman",
    preferredLanguage: "en"
  });
}

describe("playerSessionStore", () => {
  beforeEach(() => {
    usePlayerSessionStore.setState({ bySessionId: {}, sessionIdByPin: {} });
  });

  it("keys the credential by session, not by the PIN that reached it", () => {
    record("session-a", "token-a");

    // The PIN is only a hint about which record to try; the credential
    // itself belongs to one session and nothing else.
    expect(usePlayerSessionStore.getState().bySessionId["session-a"]?.resumeToken).toBe("token-a");
    expect(usePlayerSessionStore.getState().sessionIdByPin[PIN]).toBe("session-a");
  });

  it("does not let a recycled PIN carry an old session's credential forward", () => {
    record("last-months-session", "stale-token");

    // The same six digits, handed to tonight's quiz.
    record("tonights-session", "fresh-token");

    // The hint now points at tonight, so nothing offers the old credential
    // to it — the failure that resumed players into a finished quiz.
    expect(usePlayerSessionStore.getState().sessionIdByPin[PIN]).toBe("tonights-session");
    // And the old record is still filed under its own session, never
    // reachable through this PIN again.
    expect(usePlayerSessionStore.getState().bySessionId["last-months-session"]?.resumeToken).toBe(
      "stale-token"
    );
  });

  it("forgets a session's credential and the hint pointing at it together", () => {
    record("session-a", "token-a");

    usePlayerSessionStore.getState().clear("session-a");

    // A half-cleared store would leave the PIN resolving to a record that
    // is gone, and the player stuck on a resume that can never succeed.
    expect(usePlayerSessionStore.getState().bySessionId["session-a"]).toBeUndefined();
    expect(usePlayerSessionStore.getState().sessionIdByPin[PIN]).toBeUndefined();
  });

  it("leaves other sessions untouched when one is cleared", () => {
    record("session-a", "token-a");
    usePlayerSessionStore.getState().record("999999", {
      sessionId: "session-b",
      participantId: "participant-b",
      resumeToken: "token-b",
      displayName: "Aman",
      preferredLanguage: "en"
    });

    usePlayerSessionStore.getState().clear("session-a");

    expect(usePlayerSessionStore.getState().bySessionId["session-b"]?.resumeToken).toBe("token-b");
    expect(usePlayerSessionStore.getState().sessionIdByPin["999999"]).toBe("session-b");
  });
});
