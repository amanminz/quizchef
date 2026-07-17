import { useCallback, useReducer } from "react";
import type { LobbyParticipant } from "@/features/sessions/types";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { useRealtimeSubscription } from "@/realtime/useRealtime";
import type { ProtocolMessage, ParticipantPayload } from "@/types/protocol";

interface RosterState {
  participants: LobbyParticipant[];
  /** The latest roster change, phrased for the lobby's live region. */
  announcement: string;
}

function reduce(state: RosterState, message: ProtocolMessage): RosterState {
  const participantId = (message.payload as ParticipantPayload | undefined)?.participantId;
  if (!participantId) {
    return state;
  }
  const others = state.participants.filter((entry) => entry.participantId !== participantId);

  switch (message.type) {
    case "participant.joined":
      return {
        participants: [...others, { participantId, status: "connected" }],
        announcement: "A participant joined the lobby"
      };
    case "participant.disconnected":
      // Durable participants (ADR-003): a disconnect never removes the entry.
      return {
        participants: state.participants.map((entry) =>
          entry.participantId === participantId ? { ...entry, status: "disconnected" } : entry
        ),
        announcement: "A participant disconnected"
      };
    case "participant.reconnected":
      return {
        participants: [...others, { participantId, status: "connected" }],
        announcement: "A participant reconnected"
      };
    default:
      return state;
  }
}

/**
 * Transient participant presence for one session's lobby, owned by realtime
 * alone (RFC-009 state ownership): the roster exists only while this hook
 * is mounted and is rebuilt from `participant.*` events. The server's
 * `participantCount` on the session summary stays the authoritative number
 * — participants who joined before this view subscribed are in the count
 * but not in this roster, and the lobby renders that difference honestly.
 */
export function useParticipants(sessionId: string | undefined) {
  const [state, dispatch] = useReducer(reduce, { participants: [], announcement: "" });

  const handler = useCallback((message: ProtocolMessage) => dispatch(message), []);
  useRealtimeSubscription(sessionId ? sessionTopic(sessionId) : null, handler);

  return state;
}
