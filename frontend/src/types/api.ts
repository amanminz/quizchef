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

export type CreateQuizRequest = Schemas["CreateQuizRequest"];
export type UpdateQuizRequest = Schemas["UpdateQuizRequest"];
export type QuizResponse = Schemas["QuizResponse"];
export type CreateQuestionRequest = Schemas["CreateQuestionRequest"];
export type UpdateQuestionRequest = Schemas["UpdateQuestionRequest"];
export type QuestionResponse = Schemas["QuestionResponse"];

export type CreateSessionRequest = Schemas["CreateSessionRequest"];
export type SessionSummaryResponse = Schemas["SessionSummaryResponse"];
export type JoinSessionRequest = Schemas["JoinSessionRequest"];
export type ParticipantSessionResponse = Schemas["ParticipantSessionResponse"];
export type ReconnectRequest = Schemas["ReconnectRequest"];
export type SessionSnapshotResponse = Schemas["SessionSnapshotResponse"];
export type SubmitAnswerRequest = Schemas["SubmitAnswerRequest"];
export type AnswerAcceptedResponse = Schemas["AnswerAcceptedResponse"];
export type LeaderboardResponse = Schemas["LeaderboardResponse"];
