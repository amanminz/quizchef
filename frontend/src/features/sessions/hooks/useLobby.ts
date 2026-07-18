import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect } from "react";
import { useHostControls } from "@/features/sessions/hooks/useHostControls";
import { useParticipants } from "@/features/sessions/hooks/useParticipants";
import { useSession } from "@/features/sessions/hooks/useSession";
import { sessionKeys } from "@/features/sessions/queryKeys";
import { hostTopic, sessionTopic } from "@/realtime/SessionSubscriptions";
import { useConnectionStore } from "@/realtime/connectionStore";
import { useRealtimeClient, useRealtimeSubscription } from "@/realtime/useRealtime";
import type { ProtocolMessage } from "@/types/protocol";

/**
 * Everything the lobby page renders, in one orchestration hook (pages
 * render, hooks coordinate — RFC-009). The division of truth:
 *
 * - The session summary query establishes the initial state and stays the
 *   single home of server state (state, participantCount, settings, PIN).
 * - Realtime events are the change feed: every lifecycle or roster event
 *   invalidates the summary query, so pushed changes land in the same
 *   cache entry a fetch would fill — never a second copy of the session.
 * - Transient presence (who is in the roster right now) is realtime-only,
 *   owned by useParticipants.
 *
 * Nothing polls: between events the summary is simply not refetched.
 *
 * The lobby owns the realtime connection lifecycle for this PR — connect on
 * mount, disconnect on unmount. When gameplay lands (Phase 2 PR #4) the
 * connection's ownership moves up to span lobby → gameplay.
 */
export function useLobby(sessionId: string | undefined) {
  const queryClient = useQueryClient();
  const client = useRealtimeClient();
  const connectionStatus = useConnectionStore((state) => state.status);

  const sessionQuery = useSession(sessionId);
  const { participants, announcement } = useParticipants(sessionId);
  const hostControls = useHostControls(sessionQuery.data);

  useEffect(() => {
    client.connect();
    return () => {
      void client.disconnect();
    };
  }, [client]);

  const onSessionEvent = useCallback(
    (message: ProtocolMessage) => {
      switch (message.type) {
        case "lobby.opened":
        case "session.started":
        case "session.finished":
        case "participant.joined":
        case "participant.disconnected":
        case "participant.reconnected":
          // Event-driven refetch: the push tells us the summary changed,
          // the server tells us what it now is (ADR-006). Roster events
          // also refresh the name wall's read — events carry only ids.
          void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(message.sessionId) });
          void queryClient.invalidateQueries({ queryKey: sessionKeys.roster(message.sessionId) });
          break;
        default:
          break;
      }
    },
    [queryClient]
  );

  useRealtimeSubscription(sessionId ? sessionTopic(sessionId) : null, onSessionEvent);
  useRealtimeSubscription(sessionId ? hostTopic(sessionId) : null, onSessionEvent);

  return {
    session: sessionQuery.data,
    isLoading: sessionQuery.isPending,
    error: sessionQuery.error,
    refetch: sessionQuery.refetch,
    connectionStatus,
    participants,
    announcement,
    ...hostControls
  };
}
