import type { SessionSummaryResponse } from "@/types/api";

/** The RFC-004 session lifecycle, aliased from the generated DTO union. */
export type SessionState = NonNullable<SessionSummaryResponse["state"]>;

/**
 * One lobby roster entry, built purely from realtime presence events —
 * `participant.joined` / `participant.disconnected` / `participant.reconnected`
 * carry only the participant id (RFC-005 keeps roster churn cheap; display
 * names arrive with the gameplay messages that need them). Participants are
 * durable (ADR-003): a disconnect dims the entry, it never removes it.
 */
export interface LobbyParticipant {
  participantId: string;
  status: "connected" | "disconnected";
}
