import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * The ids of sessions this browser created as host — client-side knowledge,
 * so Zustand owns it (RFC-009 state ownership). The backend deliberately
 * has no "list my sessions" endpoint yet (RFC-004 Future Work), and the
 * frontend invents no API contracts: the dashboard is this local registry
 * hydrated through `GET /api/v1/sessions/{id}` detail queries, which stay
 * in TanStack Query — only the ids live here, never session data.
 *
 * Ids whose session the server no longer knows (404 — expired from the
 * Redis-backed store) are pruned by useSessions.
 */
interface HostedSessionsState {
  sessionIds: string[];
  register: (sessionId: string) => void;
  remove: (sessionId: string) => void;
}

export const useHostedSessionsStore = create<HostedSessionsState>()(
  persist(
    (set) => ({
      sessionIds: [],
      register: (sessionId) =>
        set((state) => ({
          sessionIds: [sessionId, ...state.sessionIds.filter((id) => id !== sessionId)]
        })),
      remove: (sessionId) =>
        set((state) => ({ sessionIds: state.sessionIds.filter((id) => id !== sessionId) }))
    }),
    { name: "quizchef.hostedSessions.v1" }
  )
);
