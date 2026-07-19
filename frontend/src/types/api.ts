import type { components } from "@/types/api.gen";

/**
 * Ergonomic aliases over the generated OpenAPI types (api.gen.ts). The
 * generated file is the source of truth — regenerate with `npm run
 * generate:api` — these aliases only give call sites readable names.
 */
export type Schemas = components["schemas"];

export type ApiErrorBody = Schemas["ApiError"];
export type ApiFieldError = Schemas["ApiFieldError"];

export type LoginRequest = Schemas["LoginRequest"];
export type LoginResponse = Schemas["LoginResponse"];
export type RegisterIdentityRequest = Schemas["RegisterIdentityRequest"];
export type RegisterIdentityResponse = Schemas["RegisterIdentityResponse"];
export type CurrentUserResponse = Schemas["CurrentUserResponse"];
export type HostAccessResponse = Schemas["HostAccessResponse"];

export type CreateQuizRequest = Schemas["CreateQuizRequest"];
export type UpdateQuizRequest = Schemas["UpdateQuizRequest"];
export type QuizResponse = Schemas["QuizResponse"];
export type QuizSummaryResponse = Schemas["QuizSummaryResponse"];
export type QuizPageResponse = Schemas["QuizPageResponse"];
export type AddQuestionRequest = Schemas["AddQuestionRequest"];
export type ReorderQuestionsRequest = Schemas["ReorderQuestionsRequest"];

export type CreateQuestionRequest = Schemas["CreateQuestionRequest"];
export type UpdateQuestionRequest = Schemas["UpdateQuestionRequest"];
export type QuestionResponse = Schemas["QuestionResponse"];
export type QuestionSummaryResponse = Schemas["QuestionSummaryResponse"];
export type QuestionPageResponse = Schemas["QuestionPageResponse"];
export type QuestionUsageResponse = Schemas["QuestionUsageResponse"];
export type AnswerProgressResponse = Schemas["AnswerProgressResponse"];

export type CreateSessionRequest = Schemas["CreateSessionRequest"];
export type SessionSummaryResponse = Schemas["SessionSummaryResponse"];
export type JoinSessionRequest = Schemas["JoinSessionRequest"];
export type ParticipantSessionResponse = Schemas["ParticipantSessionResponse"];
export type ReconnectRequest = Schemas["ReconnectRequest"];
export type SessionSnapshotResponse = Schemas["SessionSnapshotResponse"];
export type SubmitAnswerRequest = Schemas["SubmitAnswerRequest"];
export type AnswerAcceptedResponse = Schemas["AnswerAcceptedResponse"];
export type LeaderboardResponse = Schemas["LeaderboardResponse"];
export type CurrentQuestionResponse = Schemas["CurrentQuestionResponse"];
export type PlayableOptionDto = Schemas["PlayableOptionDto"];
export type PlayableLocalizationDto = Schemas["PlayableLocalizationDto"];
export type SessionResultsResponse = Schemas["SessionResultsResponse"];
export type LeaderboardEntryDto = Schemas["LeaderboardEntryDto"];
export type ParticipantResultResponse = Schemas["ParticipantResultResponse"];
export type SessionParticipantsResponse = Schemas["SessionParticipantsResponse"];
export type SessionParticipantDto = Schemas["SessionParticipantDto"];

/**
 * The backend has no named enum schemas — every state/type/difficulty
 * travels as an inline string union on each DTO. These aliases give the
 * same literal values one shared name for filter/param typing; they carry
 * no data shape of their own, so they don't count as hand-maintained DTOs.
 */
export type QuizState = NonNullable<QuizSummaryResponse["state"]>;
export type QuestionState = NonNullable<QuestionSummaryResponse["state"]>;
export type Difficulty = NonNullable<QuestionSummaryResponse["difficulty"]>;
export type QuestionType = NonNullable<QuestionSummaryResponse["questionType"]>;
export type SessionPhase = NonNullable<SessionSummaryResponse["currentPhase"]>;
export type PlatformRole = NonNullable<CurrentUserResponse["roles"]>[number];
export type PlatformPermission = NonNullable<CurrentUserResponse["permissions"]>[number];

/**
 * Flat pagination/sort params matching Spring's `Pageable` query
 * convention (`page`, `size`, `sort=property,direction`) — never the
 * generated `Pageable` schema shape directly, which axios would serialize
 * as nested `pageable[page]=` bracket notation Spring cannot parse.
 */
export interface PageParams {
  page?: number;
  size?: number;
  /** e.g. "updatedAt,desc" — the backend allow-lists updatedAt/createdAt/state only. */
  sort?: string;
}
