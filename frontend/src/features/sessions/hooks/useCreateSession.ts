import { useMutation, useQueryClient } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { useHostedSessionsStore } from "@/features/sessions/hostedSessionsStore";
import { sessionKeys } from "@/features/sessions/queryKeys";

/**
 * Creates a session for a published quiz. Server-confirmed like every
 * lifecycle transition (RFC-009): the server assigns the PIN and the
 * settings, the client sends only the published quiz version id — which is
 * the quiz's own id today (QuizPublicationQuery; quiz revisions are future
 * work). On success the new session is registered in the local hosted
 * registry and seeded into the query cache.
 */
export function useCreateSession() {
  const queryClient = useQueryClient();
  const register = useHostedSessionsStore((state) => state.register);

  return useMutation({
    mutationFn: (publishedQuizVersionId: string) =>
      sessionApi.create({ publishedQuizVersionId }),
    onSuccess: (session) => {
      if (session.sessionId) {
        register(session.sessionId);
        queryClient.setQueryData(sessionKeys.detail(session.sessionId), session);
      }
    }
  });
}
