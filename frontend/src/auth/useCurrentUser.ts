import { useQuery } from "@tanstack/react-query";
import { identityApi } from "@/api/identityApi";
import { useIsAuthenticated } from "@/auth/authStore";

/**
 * Who the authenticated user is — server state, so it lives in TanStack
 * Query (RFC-009 state ownership), keyed once and shared by every consumer.
 * Zustand holds only the token.
 */
export function useCurrentUser() {
  const isAuthenticated = useIsAuthenticated();

  return useQuery({
    queryKey: ["currentUser"],
    queryFn: identityApi.currentUser,
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000
  });
}
