import { participantTopic, sessionTopic } from "@/realtime/SessionSubscriptions";
import { useRealtimeSubscription } from "@/realtime/useRealtime";
import type { ProtocolMessage } from "@/types/protocol";

/**
 * Owns every realtime subscription the gameplay feature needs: the
 * session-wide broadcast (lifecycle, question/answer/leaderboard
 * progression, and roster events) and, for a participant, their private
 * topic (answer acknowledgements, future snapshot pushes). Pages never
 * subscribe directly and never build a destination — that stays
 * `SessionSubscriptions`' job (RFC-009); this hook only decides which of
 * those topics gameplay cares about and wires them to one dispatcher.
 */
export function useGameplaySubscriptions(
  sessionId: string | undefined,
  participantId: string | undefined,
  onSessionEvent: (message: ProtocolMessage) => void,
  onParticipantEvent?: (message: ProtocolMessage) => void
): void {
  useRealtimeSubscription(sessionId ? sessionTopic(sessionId) : null, onSessionEvent);
  useRealtimeSubscription(
    participantId ? participantTopic(participantId) : null,
    onParticipantEvent ?? onSessionEvent
  );
}
