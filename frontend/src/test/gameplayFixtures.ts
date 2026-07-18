import type {
  ParticipantResultResponse,
  CurrentQuestionResponse,
  LeaderboardEntryDto,
  ParticipantSessionResponse,
  SessionResultsResponse,
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

/**
 * The same question after the reveal: correctness and the explanation are
 * on the wire, exactly as the phase-gated endpoint serves them.
 */
export function revealedQuestionResponse(
  base: CurrentQuestionResponse,
  overrides: Partial<CurrentQuestionResponse> = {}
): CurrentQuestionResponse {
  return {
    ...base,
    phase: "ANSWER_REVEALED",
    endsAt: undefined,
    remainingMillis: 0,
    correctOptionIds: [base.options![0].optionId!],
    localizations: base.localizations?.map((localization) => ({
      ...localization,
      explanation: "Jonah 1:17 tells the story."
    })),
    ...overrides
  };
}

export function leaderboardEntry(
  overrides: Partial<LeaderboardEntryDto> = {}
): LeaderboardEntryDto {
  return {
    participantId: nextId("participant"),
    displayName: "Ann",
    score: 750,
    rank: 1,
    ...overrides
  };
}

export function sessionResultsResponse(
  overrides: Partial<SessionResultsResponse> = {}
): SessionResultsResponse {
  return {
    sessionId: nextId("session"),
    state: "IN_PROGRESS",
    currentPhase: "LEADERBOARD",
    totalQuestions: 2,
    participantCount: 2,
    entries: [
      leaderboardEntry({ displayName: "Ann", score: 750, rank: 1 }),
      leaderboardEntry({ displayName: "Ben", score: 320, rank: 2 })
    ],
    ...overrides
  };
}

export function participantResultResponse(
  overrides: Partial<ParticipantResultResponse> = {}
): ParticipantResultResponse {
  return {
    sessionId: nextId("session"),
    state: "IN_PROGRESS",
    currentPhase: "LEADERBOARD",
    totalQuestions: 2,
    participantCount: 2,
    participantId: "participant-me",
    displayName: "Aman",
    rank: 2,
    score: 320,
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
