import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * Authentication state — the one piece of server-issued state Zustand owns
 * (RFC-009 state ownership). Holds only the JWT and the session-expired
 * flag; who the user *is* lives in TanStack Query (useCurrentUser), never
 * duplicated here.
 *
 * The token persists to localStorage so a refresh keeps the login. The JWT
 * is session-bound server-side (RFC-002): revoking the identity session
 * invalidates it regardless of what the browser still holds.
 */
interface AuthState {
  token: string | null;
  /** True when the backend rejected the token (401) — shown on the login page. */
  sessionExpired: boolean;
  signIn: (token: string) => void;
  signOut: () => void;
  expireSession: () => void;
  acknowledgeSessionExpired: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      sessionExpired: false,
      signIn: (token) => set({ token, sessionExpired: false }),
      signOut: () => set({ token: null, sessionExpired: false }),
      expireSession: () => set({ token: null, sessionExpired: true }),
      acknowledgeSessionExpired: () => set({ sessionExpired: false })
    }),
    {
      name: "quizchef.auth.v1",
      partialize: (state) => ({ token: state.token })
    }
  )
);

export function useIsAuthenticated(): boolean {
  return useAuthStore((state) => state.token !== null);
}
