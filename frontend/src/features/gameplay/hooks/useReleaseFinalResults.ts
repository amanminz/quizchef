import { useMutation, useQueryClient } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { sessionKeys } from "@/features/sessions/queryKeys";

/**
 * The host's "Reveal Results to Participants" command — lifts the
 * final-results hold. Idempotent server-side (a duplicate release is
 * harmless), so this never needs client-side de-duplication beyond the
 * button disabling itself while in flight. Refetches the session summary
 * on success so `finalResultsReleased` (and therefore the derived
 * `FINISHED` phase) reflects reality immediately, without waiting for the
 * `final.results.revealed` broadcast.
 */
export function useReleaseFinalResults(sessionId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => sessionApi.releaseFinalResults(sessionId ?? ""),
    onSuccess: () => {
      if (sessionId) {
        void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) });
      }
    }
  });
}
