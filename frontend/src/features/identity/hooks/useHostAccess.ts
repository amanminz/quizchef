import { useMutation, useQueryClient } from "@tanstack/react-query";
import { identityApi } from "@/api/identityApi";
import { currentUserQueryKey } from "@/auth/useCurrentUser";
import { usePermissions } from "@/features/identity/hooks/usePermissions";

/**
 * The host onboarding action. Server-confirmed like every lifecycle
 * transition (RFC-009): the button waits for the 200, and the shared
 * `currentUser` query is invalidated so every role-aware surface —
 * navigation, dashboard, banners — re-renders from the server's new
 * truth. The backend authorizes from persisted roles, so the same token
 * hosts immediately; nothing client-side needs a token swap.
 */
export function useHostAccess() {
  const queryClient = useQueryClient();
  const { isHost, isLoading } = usePermissions();

  const mutation = useMutation({
    mutationFn: identityApi.requestHostAccess,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: currentUserQueryKey });
    }
  });

  return {
    isHost,
    isLoadingStatus: isLoading,
    requestHostAccess: mutation.mutateAsync,
    isRequesting: mutation.isPending,
    requestError: mutation.error,
    /** The server's verdict, once the request has run. */
    grantedStatus: mutation.data?.status
  };
}
