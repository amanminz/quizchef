import { beforeEach, describe, expect, it, vi } from "vitest";

const LEGACY_KEY = "quizchef.playerSession.v1";
const CURRENT_KEY = "quizchef.playerSession.v2";
const PIN = "042317";

/**
 * Seeds storage and then loads the store fresh, because adoption happens
 * once during rehydration — at module load.
 */
async function loadStoreWith(storage: Record<string, unknown>) {
  localStorage.clear();
  for (const [key, value] of Object.entries(storage)) {
    localStorage.setItem(key, JSON.stringify(value));
  }
  vi.resetModules();
  return (await import("@/features/gameplay/playerSessionStore")).usePlayerSessionStore;
}

function legacyStorage(records: Record<string, unknown>) {
  return { state: { bySessionPin: records }, version: 0 };
}

describe("playerSessionStore — credentials written by the PIN-keyed version", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("adopts a credential from the older storage key", async () => {
    // The exact state a player's phone was in when the session-keyed
    // release went out mid-event: a perfectly good credential, under the
    // key the new client stopped reading.
    const store = await loadStoreWith({
      [LEGACY_KEY]: legacyStorage({
        [PIN]: {
          sessionId: "session-1",
          participantId: "participant-1",
          guestParticipantToken: "token-1",
          displayName: "Aman",
          preferredLanguage: "hi"
        }
      })
    });

    expect(store.getState().sessionIdByPin[PIN]).toBe("session-1");
    expect(store.getState().bySessionId["session-1"]).toEqual({
      sessionId: "session-1",
      participantId: "participant-1",
      resumeToken: "token-1",
      displayName: "Aman",
      preferredLanguage: "hi"
    });
  });

  it("removes the old key once adopted, so a cleared credential stays cleared", async () => {
    const store = await loadStoreWith({
      [LEGACY_KEY]: legacyStorage({
        [PIN]: { sessionId: "session-1", participantId: "participant-1", guestParticipantToken: "t" }
      })
    });
    expect(localStorage.getItem(LEGACY_KEY)).toBeNull();

    // Otherwise a definitively rejected credential would be resurrected on
    // the next reload, looping the player through a resume that can never
    // succeed.
    store.getState().clear("session-1");
    expect(store.getState().sessionIdByPin[PIN]).toBeUndefined();
  });

  it("keeps a newer session-keyed record over an old one for the same session", async () => {
    const store = await loadStoreWith({
      [CURRENT_KEY]: {
        state: {
          bySessionId: {
            "session-1": {
              sessionId: "session-1",
              participantId: "participant-1",
              resumeToken: "current-token",
              displayName: "Aman",
              preferredLanguage: "en"
            }
          },
          sessionIdByPin: { [PIN]: "session-1" }
        },
        version: 0
      },
      [LEGACY_KEY]: legacyStorage({
        [PIN]: {
          sessionId: "session-1",
          participantId: "participant-1",
          guestParticipantToken: "stale-token",
          displayName: "Aman",
          preferredLanguage: "en"
        }
      })
    });

    expect(store.getState().bySessionId["session-1"]?.resumeToken).toBe("current-token");
  });

  it("skips an unusable old record without losing the usable ones beside it", async () => {
    const store = await loadStoreWith({
      [LEGACY_KEY]: legacyStorage({
        "111111": { displayName: "Half a record" },
        "222222": { sessionId: "session-2", participantId: "participant-2", guestParticipantToken: "t2" }
      })
    });

    expect(store.getState().sessionIdByPin["111111"]).toBeUndefined();
    expect(store.getState().sessionIdByPin["222222"]).toBe("session-2");
  });

  it("survives unparseable old storage", async () => {
    localStorage.clear();
    localStorage.setItem(LEGACY_KEY, "not json at all");
    vi.resetModules();
    const store = (await import("@/features/gameplay/playerSessionStore")).usePlayerSessionStore;

    // One corrupt blob must not take down the store every other player's
    // credential lives in.
    expect(store.getState().bySessionId).toEqual({});
    expect(store.getState().sessionIdByPin).toEqual({});
  });
});
