import { useMutation, useQueryClient } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { sessionKeys } from "@/features/sessions/queryKeys";
import type { SessionSummaryResponse } from "@/types/api";

/**
 * The host's lifecycle commands for one session: open the lobby (CREATED →
 * LOBBY, addressed by PIN) and start (LOBBY → IN_PROGRESS, addressed by
 * id). Both are server-confirmed with no optimistic transition — the UI
 * moves only when the 200 lands (or the corresponding realtime event
 * arrives), and a 409/403 surfaces as a normal mutation error.
 *
 * `canStart` mirrors the server's documented precondition (in LOBBY with at
 * least one participant, RFC-004) purely as button-enablement UX, the same
 * pattern as useQuizPublishing's preview: the server remains the authority,
 * and its verdict is whatever the start request returns.
 *
 * There is no cancel: the backend has no cancel/close endpoint (RFC-004);
 * unstarted sessions age out of the Redis-backed store server-side.
 */
export function useHostControls(session: SessionSummaryResponse | undefined) {
  const queryClient = useQueryClient();

  const confirmSummary = (summary: SessionSummaryResponse) => {
    if (summary.sessionId) {
      queryClient.setQueryData(sessionKeys.detail(summary.sessionId), summary);
    }
  };

  const openLobbyMutation = useMutation({
    mutationFn: (sessionPin: string) => sessionApi.openLobby(sessionPin),
    onSuccess: confirmSummary
  });

  const startMutation = useMutation({
    mutationFn: (sessionId: string) => sessionApi.start(sessionId),
    onSuccess: confirmSummary
  });

  return {
    canOpenLobby: session?.state === "CREATED",
    openLobby: openLobbyMutation.mutateAsync,
    isOpeningLobby: openLobbyMutation.isPending,
    openLobbyError: openLobbyMutation.error,
    canStart: session?.state === "LOBBY" && (session.participantCount ?? 0) > 0,
    start: startMutation.mutateAsync,
    isStarting: startMutation.isPending,
    startError: startMutation.error
  };
}
