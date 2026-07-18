import { useCurrentUser } from "@/auth/useCurrentUser";
import type { PlatformPermission, PlatformRole } from "@/types/api";

/**
 * Permission helpers over the shared `currentUser` query. Frontend
 * authorization is cosmetic (RFC-009): these decide what to *show* —
 * navigation, CTAs, banners — never what is *allowed*. The backend
 * remains authoritative, and every 403 is still handled as a real
 * outcome, not an impossibility.
 *
 * While the query is loading, everything reads as not-granted — screens
 * briefly render their least-privileged form rather than flashing
 * capabilities that may disappear.
 */
export function usePermissions() {
  const { data, isPending } = useCurrentUser();
  const roles = data?.roles ?? [];
  const permissions = data?.permissions ?? [];

  return {
    roles,
    permissions,
    isLoading: isPending,
    hasRole: (role: PlatformRole) => roles.includes(role),
    hasPermission: (permission: PlatformPermission) => permissions.includes(permission),
    /** The one product-level derivation pages actually branch on. */
    isHost: roles.includes("QUIZ_MASTER") || roles.includes("ADMIN")
  };
}
