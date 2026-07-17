import { apiClient } from "@/api/axios";
import type {
  CreateQuestionRequest,
  CreateQuizRequest,
  QuestionResponse,
  QuizResponse,
  UpdateQuestionRequest,
  UpdateQuizRequest
} from "@/types/api";

/**
 * Quiz authoring endpoints (RFC-003). Mirrors the API exactly — there are
 * no list endpoints on the backend yet, so none are invented here.
 */
export const quizApi = {
  async create(request: CreateQuizRequest): Promise<QuizResponse> {
    const { data } = await apiClient.post<QuizResponse>("/api/v1/quizzes", request);
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

  async createQuestion(request: CreateQuestionRequest): Promise<QuestionResponse> {
    const { data } = await apiClient.post<QuestionResponse>("/api/v1/questions", request);
    return data;
  },

  async getQuestionById(questionId: string): Promise<QuestionResponse> {
    const { data } = await apiClient.get<QuestionResponse>(`/api/v1/questions/${questionId}`);
    return data;
  },

  async updateQuestion(
    questionId: string,
    request: UpdateQuestionRequest
  ): Promise<QuestionResponse> {
    const { data } = await apiClient.put<QuestionResponse>(
      `/api/v1/questions/${questionId}`,
      request
    );
    return data;
  },

  async publishQuestion(questionId: string): Promise<QuestionResponse> {
    const { data } = await apiClient.post<QuestionResponse>(
      `/api/v1/questions/${questionId}/publish`
    );
    return data;
  },

  async archiveQuestion(questionId: string): Promise<QuestionResponse> {
    const { data } = await apiClient.post<QuestionResponse>(
      `/api/v1/questions/${questionId}/archive`
    );
    return data;
  }
};
