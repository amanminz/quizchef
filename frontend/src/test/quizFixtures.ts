import type {
  QuestionPageResponse,
  QuestionResponse,
  QuestionSummaryResponse,
  QuizPageResponse,
  QuizResponse,
  QuizSummaryResponse
} from "@/types/api";

let counter = 0;
function nextId(prefix: string): string {
  counter += 1;
  return `${prefix}-${counter}`;
}

export function quizSummary(overrides: Partial<QuizSummaryResponse> = {}): QuizSummaryResponse {
  return {
    id: nextId("quiz"),
    title: "Bible Quiz",
    description: "Sunday Youth Fellowship",
    state: "DRAFT",
    defaultLanguage: "en",
    questionCount: 0,
    version: 0,
    updatedAt: new Date().toISOString(),
    ...overrides
  };
}

export function quizPage(items: QuizSummaryResponse[]): QuizPageResponse {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

export function quizResponse(overrides: Partial<QuizResponse> = {}): QuizResponse {
  const id = overrides.id ?? nextId("quiz");
  return {
    id,
    ownerIdentityId: nextId("identity"),
    defaultLanguage: "en",
    state: "DRAFT",
    visibility: "PRIVATE",
    version: 0,
    settings: {
      questionTimeLimitSeconds: 30,
      randomizeQuestionOrder: false,
      randomizeOptionOrder: false,
      showLeaderboardAfterQuestion: true,
      showExplanationAfterQuestion: true
    },
    localizations: [{ languageCode: "en", title: "Bible Quiz", description: "Sunday Youth Fellowship" }],
    questionIds: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides
  };
}

export function questionSummary(overrides: Partial<QuestionSummaryResponse> = {}): QuestionSummaryResponse {
  return {
    id: nextId("question"),
    defaultLanguage: "en",
    state: "PUBLISHED",
    questionType: "TRUE_FALSE",
    difficulty: "EASY",
    title: "Jonah",
    availableLanguages: ["en"],
    tags: [],
    version: 0,
    updatedAt: new Date().toISOString(),
    ...overrides
  };
}

export function questionPage(items: QuestionSummaryResponse[]): QuestionPageResponse {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

export function questionResponse(overrides: Partial<QuestionResponse> = {}): QuestionResponse {
  const id = overrides.id ?? nextId("question");
  return {
    id,
    ownerIdentityId: nextId("identity"),
    defaultLanguage: "en",
    state: "PUBLISHED",
    questionType: "TRUE_FALSE",
    difficulty: "EASY",
    source: "MANUAL",
    version: 0,
    options: [
      { id: nextId("option"), correct: true, displayOrder: 1 },
      { id: nextId("option"), correct: false, displayOrder: 2 }
    ],
    localizations: [
      {
        languageCode: "en",
        title: "Jonah",
        prompt: "Jonah was swallowed by a great fish.",
        optionTexts: []
      }
    ],
    bibleReferences: [],
    mediaReferences: [],
    tags: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides
  };
}
