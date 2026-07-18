import { useCurrentUser } from "@/auth/useCurrentUser";
import type { PlatformRole } from "@/types/api";

/** The caller's durable roles — same shared query, list projection only. */
export function useRoles(): { roles: PlatformRole[]; isLoading: boolean } {
  const { data, isPending } = useCurrentUser();
  return { roles: data?.roles ?? [], isLoading: isPending };
}
