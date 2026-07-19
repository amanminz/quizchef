/**
 * The RFC-005 realtime wire protocol, hand-written by design: the WebSocket
 * protocol is not part of the OpenAPI document, and RFC-005 freezes these
 * shapes as the versioned public contract — they change only with a protocol
 * version bump, never with a backend refactor.
 */

/** The protocol version this client speaks. */
export const PROTOCOL_VERSION = 1;

/** The stable dotted vocabulary (RFC-005) — never backend class names. */
export type ProtocolMessageType =
  | "lobby.opened"
  | "session.started"
  | "session.finished"
  | "participant.joined"
  | "participant.disconnected"
  | "participant.reconnected"
  | "question.started"
  | "question.closed"
  | "answer.revealed"
  | "leaderboard.updated"
  | "participant.answer.accepted"
  | "answer.progress"
  | "session.snapshot";

/** The envelope every realtime message travels in. */
export interface ProtocolMessage<TPayload = unknown> {
  protocolVersion: number;
  messageId: string;
  sessionId: string;
  occurredAt: string;
  type: ProtocolMessageType;
  payload?: TPayload;
}

export interface ParticipantPayload {
  participantId: string;
}

export interface QuestionPayload {
  questionId: string;
}

/** question.started — endsAt is the server's close time; render countdowns against it (ADR-006). */
export interface QuestionStartedPayload {
  questionId: string;
  endsAt: string;
  durationSeconds: number;
}

/** answer.revealed — the first moment correctness crosses the wire (ADR-006). */
export interface AnswerRevealedPayload {
  questionId: string;
  correctOptionIds: string[];
}

export interface LeaderboardRow {
  participantId: string;
  displayName: string;
  score: number;
  rank: number;
}

export interface LeaderboardPayload {
  entries: LeaderboardRow[];
}
