import { apiClient } from "@/api/axios";
import type {
  AnswerAcceptedResponse,
  CreateSessionRequest,
  JoinSessionRequest,
  LeaderboardResponse,
  ParticipantSessionResponse,
  ReconnectRequest,
  SessionSnapshotResponse,
  SessionSummaryResponse,
  SubmitAnswerRequest
} from "@/types/api";

/**
 * Session lifecycle and gameplay endpoints (RFC-004). Host commands require
 * a bearer token; join/reconnect/answer are anonymous-friendly — the token
 * interceptor simply has nothing to inject for a guest.
 */
export const sessionApi = {
  async create(request: CreateSessionRequest): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>("/api/v1/sessions", request);
    return data;
  },

  async getById(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.get<SessionSummaryResponse>(`/api/v1/sessions/${sessionId}`);
    return data;
  },

  async openLobby(sessionPin: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionPin}/lobby`
    );
    return data;
  },

  async join(sessionPin: string, request: JoinSessionRequest): Promise<ParticipantSessionResponse> {
    const { data } = await apiClient.post<ParticipantSessionResponse>(
      `/api/v1/sessions/${sessionPin}/join`,
      request
    );
    return data;
  },

  async reconnect(request: ReconnectRequest): Promise<SessionSnapshotResponse> {
    const { data } = await apiClient.post<SessionSnapshotResponse>(
      "/api/v1/sessions/reconnect",
      request
    );
    return data;
  },

  async start(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/start`
    );
    return data;
  },

  async startQuestion(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/start`
    );
    return data;
  },

  async closeQuestion(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/close`
    );
    return data;
  },

  async revealAnswer(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/reveal`
    );
    return data;
  },

  async showLeaderboard(sessionId: string): Promise<LeaderboardResponse> {
    const { data } = await apiClient.post<LeaderboardResponse>(
      `/api/v1/sessions/${sessionId}/leaderboard`
    );
    return data;
  },

  async advanceQuestion(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/advance`
    );
    return data;
  },

  async submitAnswer(
    sessionId: string,
    request: SubmitAnswerRequest
  ): Promise<AnswerAcceptedResponse> {
    const { data } = await apiClient.post<AnswerAcceptedResponse>(
      `/api/v1/sessions/${sessionId}/answers`,
      request
    );
    return data;
  }
};
