import type {
  AnswerDistributionResponse,
  ParticipantRankContextResponse,
  ParticipantResultResponse,
  CurrentQuestionResponse,
  LeaderboardEntryDto,
  ParticipantSessionResponse,
  SessionResultsResponse,
  SessionSnapshotResponse,
  TopFiveLeaderboardTransitionResponse
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
 * The same question during its reading period: the backend genuinely
 * strips options and option texts (never merely hides them), so the
 * fixture mirrors that — an empty `options` array and an empty
 * `optionTexts` per localization, prompt untouched.
 */
export function previewQuestionResponse(
  base: CurrentQuestionResponse,
  overrides: Partial<CurrentQuestionResponse> = {}
): CurrentQuestionResponse {
  return {
    ...base,
    phase: "QUESTION_PREVIEW",
    options: [],
    correctOptionIds: undefined,
    localizations: base.localizations?.map((localization) => ({
      ...localization,
      optionTexts: []
    })),
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

/**
 * A six-player room's Top 5 transition, shaped like the real projection:
 * Fran arrives from outside the board (no previous rank), Erin drops out
 * of it (no current rank), and Ann is demoted a place without moving her
 * score. One fixture therefore exercises an entrant, a leaver, a
 * demotion, and rows that only count up.
 */
export function topFiveTransitionResponse(
  overrides: Partial<TopFiveLeaderboardTransitionResponse> = {}
): TopFiveLeaderboardTransitionResponse {
  const players = {
    ann: { participantId: "participant-ann", displayName: "Ann" },
    ben: { participantId: "participant-ben", displayName: "Ben" },
    cara: { participantId: "participant-cara", displayName: "Cara" },
    dave: { participantId: "participant-dave", displayName: "Dave" },
    erin: { participantId: "participant-erin", displayName: "Erin" },
    fran: { participantId: "participant-fran", displayName: "Fran" }
  };
  return {
    sessionId: nextId("session"),
    questionId: nextId("question"),
    questionNumber: 1,
    totalQuestions: 2,
    finalQuestion: false,
    previousTopFive: [
      { ...players.ann, previousRank: 1, currentRank: 2, previousScore: 900, currentScore: 900, pointsEarned: 0 },
      { ...players.ben, previousRank: 2, currentRank: 3, previousScore: 800, currentScore: 800, pointsEarned: 0 },
      { ...players.cara, previousRank: 3, currentRank: 4, previousScore: 700, currentScore: 700, pointsEarned: 0 },
      { ...players.dave, previousRank: 4, currentRank: 5, previousScore: 600, currentScore: 600, pointsEarned: 0 },
      // Out of the Top 5 now — no current rank is disclosed.
      { ...players.erin, previousRank: 5, previousScore: 500, currentScore: 500, pointsEarned: 0 }
    ],
    currentTopFive: [
      // Into the Top 5 from outside it — no previous rank is disclosed.
      { ...players.fran, currentRank: 1, previousScore: 400, currentScore: 1400, pointsEarned: 1000 },
      { ...players.ann, previousRank: 1, currentRank: 2, previousScore: 900, currentScore: 900, pointsEarned: 0 },
      { ...players.ben, previousRank: 2, currentRank: 3, previousScore: 800, currentScore: 800, pointsEarned: 0 },
      { ...players.cara, previousRank: 3, currentRank: 4, previousScore: 700, currentScore: 700, pointsEarned: 0 },
      { ...players.dave, previousRank: 4, currentRank: 5, previousScore: 600, currentScore: 600, pointsEarned: 0 }
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

export function answerDistributionResponse(
  overrides: Partial<AnswerDistributionResponse> = {}
): AnswerDistributionResponse {
  return {
    sessionId: nextId("session"),
    questionId: nextId("question"),
    answeredCount: 2,
    eligibleParticipantCount: 2,
    noAnswerCount: 0,
    options: [],
    ...overrides
  };
}

export function participantRankContextResponse(
  overrides: Partial<ParticipantRankContextResponse> = {}
): ParticipantRankContextResponse {
  return {
    sessionId: nextId("session"),
    participantId: "participant-me",
    displayName: "Aman",
    rank: 2,
    score: 320,
    pointsEarned: 100,
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
