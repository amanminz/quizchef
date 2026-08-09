import { useMutation, useQueryClient } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { sessionKeys } from "@/features/sessions/queryKeys";

/**
 * Shuffles the order this session will play its questions in.
 *
 * Server-confirmed, never optimistic: the host is asking the server to draw
 * an order, so there is nothing to show until it has. The session summary
 * is invalidated rather than patched, because `questionsShuffled` is the
 * server's answer to whether the draw happened, not something this client
 * can predict.
 */
export function useShuffleQuestions(sessionId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => sessionApi.shuffleQuestions(sessionId!),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId ?? "") });
    }
  });
}
