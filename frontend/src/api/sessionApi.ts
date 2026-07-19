import { apiClient } from "@/api/axios";
import type {
  AnswerAcceptedResponse,
  AnswerProgressResponse,
  CreateSessionRequest,
  CurrentQuestionResponse,
  JoinSessionRequest,
  LeaderboardResponse,
  ParticipantResultResponse,
  ParticipantSessionResponse,
  ReconnectRequest,
  SessionParticipantsResponse,
  SessionResultsResponse,
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
  },

  /**
   * The question in play, participant-safe (no correctness or explanation
   * until revealed). Public — anonymous guests read it too. Throws
   * `session.no-current-question` (409) between questions.
   */
  async currentQuestion(sessionId: string): Promise<CurrentQuestionResponse> {
    const { data } = await apiClient.get<CurrentQuestionResponse>(
      `/api/v1/sessions/${sessionId}/questions/current`
    );
    return data;
  },

  /**
   * The current question's answer progress — HOST ONLY: how many
   * participants have an accepted answer out of how many could answer
   * right now. Counts only, never who. Refreshed on each answer.progress
   * broadcast; throws `session.no-current-question` (409) between
   * questions.
   */
  async answerProgress(sessionId: string): Promise<AnswerProgressResponse> {
    const { data } = await apiClient.get<AnswerProgressResponse>(
      `/api/v1/sessions/${sessionId}/answer-progress`
    );
    return data;
  },

  /**
   * The full standings — HOST ONLY since the live-event privacy split:
   * every name, score, and rank is the host's projection. Interim (once
   * revealed / on the leaderboard) and final (after FINISHED) share this
   * one shape; throws `session.results.not-available` (409) while a
   * question is still being played. Distinct from `showLeaderboard`, the
   * host's phase-transitioning command — this never changes anything.
   */
  async results(sessionId: string): Promise<SessionResultsResponse> {
    const { data } = await apiClient.get<SessionResultsResponse>(
      `/api/v1/sessions/${sessionId}/results`
    );
    return data;
  },

  /**
   * One participant's own row — rank, score, framing counts, and nothing
   * about anyone else. Anonymous-friendly: the unguessable session and
   * participant ids gate it, the same trust `submitAnswer` places in the
   * participant id. Phase-gated exactly like `results`.
   */
  async participantResult(
    sessionId: string,
    participantId: string
  ): Promise<ParticipantResultResponse> {
    const { data } = await apiClient.get<ParticipantResultResponse>(
      `/api/v1/sessions/${sessionId}/participants/${participantId}/result`
    );
    return data;
  },

  /**
   * The roster, host only: every joined participant's display name and
   * connection state in stable join order — what the projected lobby wall
   * renders. Join events carry only ids, so the wall re-reads this on each
   * roster event.
   */
  async participants(sessionId: string): Promise<SessionParticipantsResponse> {
    const { data } = await apiClient.get<SessionParticipantsResponse>(
      `/api/v1/sessions/${sessionId}/participants`
    );
    return data;
  }
};
