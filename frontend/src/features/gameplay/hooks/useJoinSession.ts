import { useMutation } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";
import type { JoinSessionRequest } from "@/types/api";

export interface JoinSessionVariables {
  pin: string;
  request: JoinSessionRequest;
}

/**
 * Joins a session by PIN and records the resulting participant locally
 * (`playerSessionStore`) so a refresh or a return visit to the same PIN
 * can reconnect instead of joining again. Open to everyone — guests join
 * anonymously; a signed-in caller joins backed by their identity (the
 * axios interceptor attaches the bearer token automatically, the join
 * form never branches on auth state). The PIN travels with each call
 * (rather than being fixed at hook-construction time) so the same hook
 * serves both the bare `/play` entry, where the PIN is whatever the
 * visitor just typed, and `/play/:pin`, where it's fixed from the route.
 */
export function useJoinSession() {
  const record = usePlayerSessionStore((state) => state.record);

  return useMutation({
    mutationFn: ({ pin, request }: JoinSessionVariables) => sessionApi.join(pin, request),
    onSuccess: (participant, { pin, request }) => {
      if (participant.participantId && participant.sessionId) {
        record(pin, {
          sessionId: participant.sessionId,
          participantId: participant.participantId,
          guestParticipantToken: participant.guestParticipantToken ?? undefined,
          displayName: request.displayName ?? "",
          preferredLanguage: request.preferredLanguage ?? "en"
        });
      }
    }
  });
}
