import type { SessionSummaryResponse } from "@/types/api";
import { testIdentity } from "@/test/handlers";

let counter = 0;
function nextId(prefix: string): string {
  counter += 1;
  return `${prefix}-${counter}`;
}

export function sessionSummary(
  overrides: Partial<SessionSummaryResponse> = {}
): SessionSummaryResponse {
  return {
    sessionId: nextId("session"),
    sessionPin: "042317",
    state: "CREATED",
    currentPhase: undefined,
    hostIdentityId: testIdentity.identityId,
    publishedQuizVersionId: nextId("quiz"),
    participantCount: 0,
    settings: {
      allowLateJoin: false,
      allowReconnect: true,
      showLiveLeaderboard: true,
      maxParticipants: 100
    },
    version: 0,
    createdAt: new Date().toISOString(),
    ...overrides
  };
}
