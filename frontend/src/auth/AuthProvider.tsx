import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo, type ReactNode } from "react";
import { identityApi } from "@/api/identityApi";
import { AuthContext } from "@/auth/AuthContext";
import { useAuthStore } from "@/auth/authStore";

/**
 * Coordinates authentication: login stores the session-bound JWT (RFC-002),
 * logout discards it. No business logic — credentials go to the backend and
 * the backend decides.
 *
 * Logout is client-side only: the backend has no logout endpoint yet
 * (RFC-002 Future Work); tokens are session-bound server-side, so revoking
 * the identity session remains the server's kill switch. Likewise there is
 * no token refresh — the backend does not issue refresh tokens — so an
 * expired or revoked token surfaces as a 401, which clears local auth and
 * routes back to the login page (see api/axios.ts).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const token = useAuthStore((state) => state.token);
  const signIn = useAuthStore((state) => state.signIn);
  const signOut = useAuthStore((state) => state.signOut);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await identityApi.login({ email, password });
      signIn(response.token ?? "");
      // A new principal invalidates everything cached for the old one.
      await queryClient.invalidateQueries();
    },
    [signIn, queryClient]
  );

  const logout = useCallback(() => {
    signOut();
    queryClient.clear();
  }, [signOut, queryClient]);

  const value = useMemo(
    () => ({ isAuthenticated: token !== null, login, logout }),
    [token, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
