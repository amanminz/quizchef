import { apiClient } from "@/api/axios";
import type {
  CreateQuestionRequest,
  Difficulty,
  PageParams,
  QuestionPageResponse,
  QuestionResponse,
  QuestionState,
  UpdateQuestionRequest
} from "@/types/api";

export interface QuestionLibraryFilters extends PageParams {
  state?: QuestionState;
  difficulty?: Difficulty;
  /** BCP-47 tag; matches questions with a localization in this language. */
  language?: string;
  /** A question matches if it has at least one of these tag ids. */
  tags?: string[];
  /** Case-insensitive match against any localization's title or prompt. */
  search?: string;
}

/**
 * The question library endpoints (RFC-003): author once, reuse across
 * quizzes. Question creation/editing is out of scope for the authoring UI
 * (Phase 2 PR #2) — only the read/search side is consumed by the picker for
 * now, but the full CRUD surface is mirrored here for the module that owns
 * question authoring later.
 */
export const questionApi = {
  async create(request: CreateQuestionRequest): Promise<QuestionResponse> {
    const { data } = await apiClient.post<QuestionResponse>("/api/v1/questions", request);
    return data;
  },

  /** The caller's own library, filtered and paged. Never cross-author. */
  async search(filters: QuestionLibraryFilters = {}): Promise<QuestionPageResponse> {
    const { data } = await apiClient.get<QuestionPageResponse>("/api/v1/questions", {
      params: filters
    });
    return data;
  },

  async getById(questionId: string): Promise<QuestionResponse> {
    const { data } = await apiClient.get<QuestionResponse>(`/api/v1/questions/${questionId}`);
    return data;
  },

  async update(questionId: string, request: UpdateQuestionRequest): Promise<QuestionResponse> {
    const { data } = await apiClient.put<QuestionResponse>(
      `/api/v1/questions/${questionId}`,
      request
    );
    return data;
  },

  async publish(questionId: string): Promise<QuestionResponse> {
    const { data } = await apiClient.post<QuestionResponse>(
      `/api/v1/questions/${questionId}/publish`
    );
    return data;
  },

  async archive(questionId: string): Promise<QuestionResponse> {
    const { data } = await apiClient.post<QuestionResponse>(
      `/api/v1/questions/${questionId}/archive`
    );
    return data;
  }
};
