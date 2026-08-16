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

const PERSIST_KEY = "quizchef.playerSession.v2";
/** Where credentials lived before they were keyed by session. */
const LEGACY_PERSIST_KEY = "quizchef.playerSession.v1";

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
    {
      name: PERSIST_KEY,
      // v1 was keyed by PIN. Dropping those records rather than carrying
      // them over was a mistake that stranded a live event: every player
      // whose browser held one lost their credential on deploy, never
      // attempted a resume, and was sent to the join form — where the
      // display-name rule that shipped in the same release refused their
      // own name.
      //
      // The PIN *hint* was the untrustworthy part, never the credential.
      // The record names the session it belongs to, and the server now
      // resolves the PIN itself and refuses a credential belonging to a
      // different session — so a stale record costs one rejected resume,
      // and carrying it over costs nothing.
      //
      // Done in `merge` rather than `migrate` because zustand only migrates
      // within one storage key, and v1's records live under a different one.
      merge: (persisted, current) => ({
        ...current,
        ...(persisted as Partial<PlayerSessionState>),
        ...adoptLegacyRecords(persisted as Partial<PlayerSessionState>)
      })
    }
  )
);

/** The v1 shape: one record per PIN, with the token under its old name. */
interface LegacyRecord {
  sessionId?: string;
  participantId?: string;
  guestParticipantToken?: string;
  displayName?: string;
  preferredLanguage?: string;
}

/**
 * Folds any credentials left in v1's storage key into this store, then
 * removes them.
 *
 * <p>Removing matters as much as adopting. Left in place, v1 would be
 * re-read on every reload — including after a resume was definitively
 * rejected and the record deliberately cleared — putting the player in a
 * loop that retries a credential the server has already refused.
 *
 * <p>An existing v2 record always wins: it was written more recently, by a
 * client that already knew about session-scoped storage.
 *
 * <p>Deliberately forgiving throughout. A half-written or hand-edited
 * record, or storage that throws (Safari private mode), should cost that
 * one player a rejoin — never take the whole store, and everyone else's
 * credential, down with it.
 */
function adoptLegacyRecords(persisted: Partial<PlayerSessionState> | undefined): {
  bySessionId: Record<string, PlayerSessionRecord>;
  sessionIdByPin: Record<string, string>;
} {
  const bySessionId = { ...(persisted?.bySessionId ?? {}) };
  const sessionIdByPin = { ...(persisted?.sessionIdByPin ?? {}) };
  try {
    const raw = globalThis.localStorage?.getItem(LEGACY_PERSIST_KEY);
    if (!raw) {
      return { bySessionId, sessionIdByPin };
    }
    const legacy: Record<string, LegacyRecord> =
      JSON.parse(raw)?.state?.bySessionPin ?? {};
    for (const [pin, record] of Object.entries(legacy)) {
      if (!record?.sessionId || !record.participantId || bySessionId[record.sessionId]) {
        continue;
      }
      bySessionId[record.sessionId] = {
        sessionId: record.sessionId,
        participantId: record.participantId,
        resumeToken: record.guestParticipantToken,
        displayName: record.displayName ?? "",
        preferredLanguage: record.preferredLanguage ?? "en"
      };
      sessionIdByPin[pin] = record.sessionId;
    }
    globalThis.localStorage?.removeItem(LEGACY_PERSIST_KEY);
  } catch {
    // Unreadable or unwritable storage: carry on with whatever v2 holds.
  }
  return { bySessionId, sessionIdByPin };
}

/** The stored record to try for this PIN, if there is one. */
export function useStoredPlayerSession(pin: string): PlayerSessionRecord | undefined {
  return usePlayerSessionStore((state) => {
    const sessionId = state.sessionIdByPin[pin];
    return sessionId ? state.bySessionId[sessionId] : undefined;
  });
}
