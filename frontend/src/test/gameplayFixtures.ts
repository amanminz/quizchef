import type {
  CurrentQuestionResponse,
  ParticipantSessionResponse,
  SessionSnapshotResponse
} from "@/types/api";

let counter = 0;
function nextId(prefix: string): string {
  counter += 1;
  return `${prefix}-${counter}`;
}

export function currentQuestionResponse(
  overrides: Partial<CurrentQuestionResponse> = {}
): CurrentQuestionResponse {
  const correctOptionId = nextId("option");
  const wrongOptionId = nextId("option");
  return {
    sessionId: nextId("session"),
    questionId: nextId("question"),
    phase: "QUESTION_OPEN",
    questionNumber: 1,
    totalQuestions: 2,
    questionType: "TRUE_FALSE",
    defaultLanguage: "en",
    durationSeconds: 30,
    endsAt: new Date(Date.now() + 20_000).toISOString(),
    remainingMillis: 20_000,
    options: [
      { optionId: correctOptionId, displayOrder: 1 },
      { optionId: wrongOptionId, displayOrder: 2 }
    ],
    localizations: [
      {
        languageCode: "en",
        prompt: "Jonah was swallowed by a great fish.",
        optionTexts: [
          { optionId: correctOptionId, text: "True" },
          { optionId: wrongOptionId, text: "False" }
        ]
      }
    ],
    correctOptionIds: undefined,
    ...overrides
  };
}

export function participantSessionResponse(
  overrides: Partial<ParticipantSessionResponse> = {}
): ParticipantSessionResponse {
  return {
    participantId: nextId("participant"),
    sessionId: nextId("session"),
    guestParticipantToken: nextId("guest-token"),
    sessionState: "LOBBY",
    ...overrides
  };
}

export function sessionSnapshotResponse(
  overrides: Partial<SessionSnapshotResponse> = {}
): SessionSnapshotResponse {
  return {
    sessionId: nextId("session"),
    participantId: nextId("participant"),
    sessionState: "IN_PROGRESS",
    currentQuestionId: undefined,
    currentPhase: "QUESTION_OPEN",
    remainingMillis: 20_000,
    participantScore: 0,
    submittedOptionIds: [],
    leaderboard: [],
    ...overrides
  };
}
