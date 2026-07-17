import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * One participant's join record for one session — resolved once from
 * `POST /sessions/{pin}/join` and durable exactly like the Participant it
 * points to (ADR-003). Kept client-side so a refresh (or returning via the
 * same PIN link) can rebind through `POST /sessions/reconnect` without the
 * player rejoining. `guestParticipantToken` is present for guests only;
 * a registered player reconnects on their bearer token instead, so only
 * `sessionId` is needed for that path (RFC-009 resolves its own Open
 * Question here, mirroring PR #3's hostedSessionsStore: client-side facts
 * only, never session data itself).
 */
export interface PlayerSessionRecord {
  sessionId: string;
  participantId: string;
  guestParticipantToken?: string;
  /** The nickname chosen at join, reused if the player rejoins. */
  displayName: string;
  preferredLanguage: string;
}

interface PlayerSessionState {
  /** Keyed by session PIN — the identifier the player actually knows. */
  bySessionPin: Record<string, PlayerSessionRecord>;
  record: (pin: string, entry: PlayerSessionRecord) => void;
  clear: (pin: string) => void;
}

export const usePlayerSessionStore = create<PlayerSessionState>()(
  persist(
    (set) => ({
      bySessionPin: {},
      record: (pin, entry) =>
        set((state) => ({ bySessionPin: { ...state.bySessionPin, [pin]: entry } })),
      clear: (pin) =>
        set((state) => ({
          bySessionPin: Object.fromEntries(
            Object.entries(state.bySessionPin).filter(([storedPin]) => storedPin !== pin)
          )
        }))
    }),
    { name: "quizchef.playerSession.v1" }
  )
);
