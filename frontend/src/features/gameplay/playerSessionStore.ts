import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * One participant's resume credential for one session — issued once by
 * `POST /sessions/{pin}/join` and durable exactly like the Participant it
 * points to (ADR-003). Kept in persistent storage so an ordinary browser
 * close and reopen still returns the same player, with the same score, via
 * `POST /sessions/{pin}/participants/resume`.
 *
 * `resumeToken` is present for guests only; a registered player resumes on
 * their bearer token instead, so their record carries no secret at all.
 */
export interface PlayerSessionRecord {
  sessionId: string;
  participantId: string;
  resumeToken?: string;
  /** The nickname chosen at join. */
  displayName: string;
  preferredLanguage: string;
}

interface PlayerSessionState {
  /**
   * Keyed by session **id**, never by PIN.
   *
   * PINs are recycled: the backend only keeps one unique among *active*
   * sessions, so tonight's quiz can be handed the same six digits last
   * month's used. Keyed by PIN, a returning player's stored credential
   * would be offered to whichever session currently owns that code — and
   * before this was fixed, the global token lookup happily resumed them
   * into the old, finished quiz instead of the one in the room. Keyed by
   * session id, a credential can only ever be presented for the session it
   * was issued by.
   */
  bySessionId: Record<string, PlayerSessionRecord>;
  /**
   * The last session each PIN resolved to — a hint, not an authority.
   *
   * A player arriving at `/play/123456` knows only the PIN, so something
   * has to nominate which stored record to try. The server then decides:
   * it resolves the PIN to whichever session is live under it now and
   * refuses a credential that does not belong to that session, so a stale
   * hint costs one rejected resume and nothing else.
   */
  sessionIdByPin: Record<string, string>;
  record: (pin: string, entry: PlayerSessionRecord) => void;
  /** Forgets one session's credential — a rejected resume, or a finished quiz. */
  clear: (sessionId: string) => void;
}

export const usePlayerSessionStore = create<PlayerSessionState>()(
  persist(
    (set) => ({
      bySessionId: {},
      sessionIdByPin: {},
      record: (pin, entry) =>
        set((state) => ({
          bySessionId: { ...state.bySessionId, [entry.sessionId]: entry },
          sessionIdByPin: { ...state.sessionIdByPin, [pin]: entry.sessionId }
        })),
      clear: (sessionId) =>
        set((state) => ({
          bySessionId: Object.fromEntries(
            Object.entries(state.bySessionId).filter(([stored]) => stored !== sessionId)
          ),
          sessionIdByPin: Object.fromEntries(
            Object.entries(state.sessionIdByPin).filter(([, stored]) => stored !== sessionId)
          )
        }))
    }),
    // v2: v1 was keyed by PIN and its records are unsafe to reuse for the
    // reason above. Nothing migrates — a v1 record is dropped, and its
    // owner rejoins, which is the same outcome they would have got from a
    // cleared browser and strictly better than being restored into the
    // wrong quiz.
    { name: "quizchef.playerSession.v2" }
  )
);

/** The stored record to try for this PIN, if there is one. */
export function useStoredPlayerSession(pin: string): PlayerSessionRecord | undefined {
  return usePlayerSessionStore((state) => {
    const sessionId = state.sessionIdByPin[pin];
    return sessionId ? state.bySessionId[sessionId] : undefined;
  });
}
