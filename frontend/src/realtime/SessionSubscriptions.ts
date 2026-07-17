import type { RealtimeClient, RealtimeMessageHandler } from "@/realtime/RealtimeClient";

/**
 * The RFC-005 topic hierarchy, mirrored from the backend's Topics class —
 * the only place the frontend builds a destination string. Feature code
 * subscribes through these; gameplay PRs add typed payload routing on top.
 */
export const sessionTopic = (sessionId: string) => `/topic/session/${sessionId}`;
export const participantTopic = (participantId: string) => `/topic/participant/${participantId}`;
export const hostTopic = (sessionId: string) => `/topic/host/${sessionId}`;

/** Everything broadcast to a session's audience. */
export function subscribeToSession(
  client: RealtimeClient,
  sessionId: string,
  handler: RealtimeMessageHandler
): () => void {
  return client.subscribe(sessionTopic(sessionId), handler);
}

/** Private messages for one participant (answer acks, snapshots). */
export function subscribeToParticipant(
  client: RealtimeClient,
  participantId: string,
  handler: RealtimeMessageHandler
): () => void {
  return client.subscribe(participantTopic(participantId), handler);
}

/** The host's control channel. */
export function subscribeToHost(
  client: RealtimeClient,
  sessionId: string,
  handler: RealtimeMessageHandler
): () => void {
  return client.subscribe(hostTopic(sessionId), handler);
}
