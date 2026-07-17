import { apiClient } from "@/api/axios";
import type {
  CreateQuizRequest,
  PageParams,
  QuizPageResponse,
  QuizResponse,
  QuizState,
  UpdateQuizRequest
} from "@/types/api";

export interface QuizListFilters extends PageParams {
  state?: QuizState;
  /** Case-insensitive match against any localization's title. */
  search?: string;
}

/**
 * Quiz authoring endpoints (RFC-003). Mirrors the API exactly — composition
 * (attach/detach/reorder questions) lives here too, since it acts on the
 * quiz's own composition; question CRUD is `questionApi`.
 */
export const quizApi = {
  async create(request: CreateQuizRequest): Promise<QuizResponse> {
    const { data } = await apiClient.post<QuizResponse>("/api/v1/quizzes", request);
    return data;
  },

  /** "My Quizzes" — owner-scoped, filtered, paged. There is no list-all endpoint. */
  async listMine(filters: QuizListFilters = {}): Promise<QuizPageResponse> {
    const { data } = await apiClient.get<QuizPageResponse>("/api/v1/quizzes/mine", {
      params: filters
    });
    return data;
  },

  async getById(quizId: string): Promise<QuizResponse> {
    const { data } = await apiClient.get<QuizResponse>(`/api/v1/quizzes/${quizId}`);
    return data;
  },

  async update(quizId: string, request: UpdateQuizRequest): Promise<QuizResponse> {
    const { data } = await apiClient.put<QuizResponse>(`/api/v1/quizzes/${quizId}`, request);
    return data;
  },

  async publish(quizId: string): Promise<QuizResponse> {
    const { data } = await apiClient.post<QuizResponse>(`/api/v1/quizzes/${quizId}/publish`);
    return data;
  },

  async archive(quizId: string): Promise<QuizResponse> {
    const { data } = await apiClient.post<QuizResponse>(`/api/v1/quizzes/${quizId}/archive`);
    return data;
  },

  /** Attaches a draft or published question (not archived) to the end of the composition. */
  async attachQuestion(quizId: string, questionId: string): Promise<QuizResponse> {
    const { data } = await apiClient.post<QuizResponse>(`/api/v1/quizzes/${quizId}/questions`, {
      questionId
    });
    return data;
  },

  /** Detaches a question. Draft quizzes only. */
  async detachQuestion(quizId: string, questionId: string): Promise<QuizResponse> {
    const { data } = await apiClient.delete<QuizResponse>(
      `/api/v1/quizzes/${quizId}/questions/${questionId}`
    );
    return data;
  },

  /** Reorders the composition. Draft quizzes only; must name every current question exactly once. */
  async reorderQuestions(quizId: string, questionIds: string[]): Promise<QuizResponse> {
    const { data } = await apiClient.patch<QuizResponse>(
      `/api/v1/quizzes/${quizId}/questions/order`,
      { questionIds }
    );
    return data;
  }
};
