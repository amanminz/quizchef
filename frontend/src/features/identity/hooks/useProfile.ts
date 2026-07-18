import { useCurrentUser } from "@/auth/useCurrentUser";

/**
 * The caller's own profile — the identity feature's read over the one
 * shared `currentUser` query (never a second cache entry: RFC-009's
 * one-owner rule). `/users/me` carries the profile basics since Phase 3,
 * so this is a projection, not a new request.
 */
export function useProfile() {
  const { data, isPending, isError, error, refetch } = useCurrentUser();

  return {
    identityId: data?.identityId,
    identityType: data?.identityType,
    displayName: data?.displayName,
    email: data?.email,
    roles: data?.roles ?? [],
    permissions: data?.permissions ?? [],
    isPending,
    isError,
    error,
    refetch
  };
}
