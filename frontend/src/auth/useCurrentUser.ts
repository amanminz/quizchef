import { useQuery } from "@tanstack/react-query";
import { identityApi } from "@/api/identityApi";
import { useIsAuthenticated } from "@/auth/authStore";

/**
 * The one query key for "who am I" — exported so the identity feature's
 * mutations (host onboarding) invalidate the same entry every consumer
 * reads; roles and permissions never get a second cache home.
 */
export const currentUserQueryKey = ["currentUser"] as const;

/**
 * Who the authenticated user is — server state, so it lives in TanStack
 * Query (RFC-009 state ownership), keyed once and shared by every consumer.
 * Zustand holds only the token.
 */
export function useCurrentUser() {
  const isAuthenticated = useIsAuthenticated();

  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: identityApi.currentUser,
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000
  });
}
